package sg.org.bcc.attendance.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import sg.org.bcc.attendance.data.local.dao.*
import sg.org.bcc.attendance.data.local.entities.*
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.data.remote.PushResult
import sg.org.bcc.attendance.sync.DatabaseSyncLogScope
import sg.org.bcc.attendance.sync.NoOpSyncLogScope
import sg.org.bcc.attendance.sync.SyncLogScope
import sg.org.bcc.attendance.sync.SyncScheduler
import sg.org.bcc.attendance.util.EventSuggester
import sg.org.bcc.attendance.util.time.TimeProvider
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class QueueItem(
    val attendee: Attendee,
    val isLater: Boolean
)

@Singleton
class AttendanceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val attendeeDao: AttendeeDao,
    private val attendanceDao: AttendanceDao,
    private val persistentQueueDao: PersistentQueueDao,
    private val syncJobDao: SyncJobDao,
    private val queueArchiveDao: QueueArchiveDao,
    private val eventDao: EventDao,
    private val groupDao: GroupDao,
    private val attendeeGroupMappingDao: AttendeeGroupMappingDao,
    private val syncLogDao: SyncLogDao,
    private val cloudProvider: AttendanceCloudProvider,
    private val authManager: AuthManager,
    private val timeProvider: TimeProvider,
    private val syncScheduler: SyncScheduler
) {
    companion object {
        private const val PREFS_NAME = "sync_prefs"
        private const val PREF_MASTER_VERSION = "local_master_list_version"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSyncLogsSummary() = syncLogDao.getTriggersSummary()
    fun getLogsForTrigger(triggerId: String) = syncLogDao.getLogsForTrigger(triggerId)

    fun getQueueItems(): Flow<List<QueueItem>> {
        return combine(
            attendeeDao.getAllAttendees(),
            persistentQueueDao.getQueue()
        ) { attendees, queue ->
            val attendeeMap = attendees.associateBy { it.id }
            queue.mapNotNull { q ->
                attendeeMap[q.attendeeId]?.let { attendee ->
                    QueueItem(attendee, q.isLater)
                }
            }
        }
    }

    suspend fun syncQueue(eventId: String, state: String = "PRESENT") {
        val currentQueue = persistentQueueDao.getQueue().first()
        if (currentQueue.isEmpty()) return

        val timestamp = timeProvider.now()
        val readyItems = currentQueue.filter { !it.isLater }
        if (readyItems.isEmpty()) return

        // 1. Fetch attendee details for name mapping
        val attendees = attendeeDao.getAllAttendees().first().associateBy { it.id }

        val payload = readyItems.map { q ->
            val name = attendees[q.attendeeId]?.fullName ?: "Unknown"
            // Escape name for JSON
            val escapedName = name.replace("\"", "\\\"")
            "{\"id\":\"${q.attendeeId}\",\"name\":\"$escapedName\",\"state\":\"$state\",\"time\":$timestamp}"
        }.joinToString(prefix = "[", postfix = "]", separator = ",")

        // Local Write: Update local attendance state immediately for UI feedback
        val records = readyItems.map { q ->
            AttendanceRecord(
                eventId = eventId,
                attendeeId = q.attendeeId,
                fullName = attendees[q.attendeeId]?.fullName ?: "",
                state = state,
                timestamp = timestamp
            )
        }
        attendanceDao.upsertAllIfNewer(records)

        // All commits create a SyncJob, processed by SyncWorker via CloudProvider interface
        val job = SyncJob(
            eventId = eventId,
            payloadJson = payload,
            createdAt = timestamp
        )
        syncJobDao.insert(job)

        // Archive before clearing (only archive what was committed)
        val archive = QueueArchive(
            eventId = eventId,
            timestamp = timestamp,
            dataJson = payload
        )
        queueArchiveDao.insertWithFifo(archive)

        // Only clear the items that were actually committed
        persistentQueueDao.clearReady()

        // Trigger background sync
        syncScheduler.scheduleSync()
    }

    suspend fun retrySync() {
        syncScheduler.scheduleSync()
    }

    fun getArchives(): Flow<List<QueueArchive>> = queueArchiveDao.getAllArchives()

    fun getAllAttendees(): Flow<List<Attendee>> = attendeeDao.getAllAttendees()

    suspend fun isDemoMode(): Boolean {
        return authManager.isDemoMode.value
    }

    private suspend fun checkAuthAndRefresh(): Boolean {
        if (isDemoMode()) return true
        if (authManager.isTokenExpired()) {
            return authManager.silentRefresh()
        }
        return true
    }

    suspend fun syncMasterList(targetEventId: String? = null) {
        syncMasterListWithDetailedResult(isFullSync = true, triggerType = "APP_START", targetEventId = targetEventId)
    }

    suspend fun syncMasterListWithResult(targetEventId: String? = null): Boolean {
        return syncMasterListWithDetailedResult(isFullSync = true, triggerType = "MANUAL", targetEventId = targetEventId).first
    }

    suspend fun syncMasterListWithDetailedResult(
        isFullSync: Boolean = true,
        triggerType: String = "MANUAL",
        targetEventId: String? = null
    ): Pair<Boolean, String> {
        val scope = DatabaseSyncLogScope(syncLogDao, triggerType)
        
        if (!checkAuthAndRefresh()) {
            scope.log("authCheck", false, "Authentication failed")
            return false to "Authentication failed or token expired."
        }

        if (syncJobDao.getPendingCount() > 0) {
            scope.log("preCheck", false, "Skipped pull due to pending sync jobs")
            return false to "Skipped pull due to pending sync jobs."
        }

        val status = mutableListOf<String>()
        try {
            android.util.Log.d("AttendanceSync", "Starting master list sync (isFullSync=$isFullSync, target=$targetEventId)...")
            
            if (isFullSync) {
                // 1-3. Master Data (Version-Based)
                val localVersion = prefs.getString(PREF_MASTER_VERSION, "")
                val remoteVersion = try {
                    cloudProvider.fetchMasterListVersion(scope).also {
                        android.util.Log.d("AttendanceSync", "Remote Master Version: $it (Local: $localVersion)")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceSync", "Failed to fetch master list version", e)
                    "" 
                }

                if (remoteVersion.isNotEmpty() && remoteVersion == localVersion) {
                    status.add("Master List: Already up to date (version: $remoteVersion)")
                } else {
                    // Fetch Attendees
                    try {
                        val localAttendeeIds = attendeeDao.getAllAttendeeIds().toSet()
                        val remoteAttendees = cloudProvider.fetchMasterAttendees(scope)
                        val remoteAttendeeIds = remoteAttendees.map { it.id }.toSet()
                        
                        attendeeDao.insertAll(remoteAttendees)
                        
                        val missingAttendeeIds = localAttendeeIds.filter { it !in remoteAttendeeIds }
                        if (missingAttendeeIds.isNotEmpty()) {
                            attendeeDao.markAsMissingOnCloud(missingAttendeeIds)
                            status.add("Attendees: OK (${remoteAttendees.size}), Missing on cloud: ${missingAttendeeIds.size}")
                        } else {
                            status.add("Attendees: OK (${remoteAttendees.size})")
                        }
                    } catch (e: Exception) {
                        status.add("Attendees: FAILED (${e.message})")
                        throw e 
                    }

                    // Groups (Non-critical)
                    try {
                        val localGroupIds = groupDao.getAllGroupIds().toSet()
                        val remoteGroups = cloudProvider.fetchMasterGroups(scope)
                        val remoteGroupIds = remoteGroups.map { it.groupId }.toSet()
                        
                        // We used to clearAll here, but now we should only update/insert and mark missing.
                        // groupDao.clearAll() 
                        if (remoteGroups.isNotEmpty()) {
                            groupDao.insertAll(remoteGroups)
                        }
                        
                        val missingGroupIds = localGroupIds.filter { it !in remoteGroupIds }
                        if (missingGroupIds.isNotEmpty()) {
                            groupDao.markAsMissingOnCloud(missingGroupIds)
                            status.add("Groups: OK (${remoteGroups.size}), Missing on cloud: ${missingGroupIds.size}")
                        } else {
                            status.add("Groups: OK (${remoteGroups.size})")
                        }
                    } catch (e: Exception) {
                        status.add("Groups: FAILED (${e.message})")
                    }

                    // Mappings (Non-critical)
                    try {
                        val remoteMappings = cloudProvider.fetchAttendeeGroupMappings(scope)
                        attendeeGroupMappingDao.clearAll()
                        if (remoteMappings.isNotEmpty()) {
                            attendeeGroupMappingDao.insertAll(remoteMappings)
                            
                            // Requirement: If a mapping references an unknown ID, create placeholder.
                            val currentAttendeeIds = attendeeDao.getAllAttendeeIds().toSet()
                            val currentGroupIds = groupDao.getAllGroupIds().toSet()
                            
                            val missingAttendeeIds = remoteMappings.map { it.attendeeId }.toSet() - currentAttendeeIds
                            val missingGroupIds = remoteMappings.map { it.groupId }.toSet() - currentGroupIds
                            
                            if (missingAttendeeIds.isNotEmpty()) {
                                android.util.Log.d("AttendanceSync", "Creating ${missingAttendeeIds.size} attendee placeholders from mappings")
                                attendeeDao.insertAll(missingAttendeeIds.map { 
                                    Attendee(id = it, fullName = it, notExistOnCloud = true) 
                                })
                            }
                            
                            if (missingGroupIds.isNotEmpty()) {
                                android.util.Log.d("AttendanceSync", "Creating ${missingGroupIds.size} group placeholders from mappings")
                                groupDao.insertAll(missingGroupIds.map { 
                                    Group(groupId = it, name = it, notExistOnCloud = true) 
                                })
                            }
                        }
                        status.add("Mappings: OK (${remoteMappings.size})")
                    } catch (e: Exception) {
                        status.add("Mappings: FAILED (${e.message})")
                    }

                    if (remoteVersion.isNotEmpty()) {
                        prefs.edit().putString(PREF_MASTER_VERSION, remoteVersion).apply()
                    }
                }

                // 4. Events Discovery (Discovery of new/deleted events)
                try {
                    val remoteEvents = cloudProvider.fetchRecentEvents(30, scope)
                    if (remoteEvents.isNotEmpty()) {
                        mergeAndInsertEvents(remoteEvents)
                    }
                    status.add("Events Discovery: OK (${remoteEvents.size})")
                } catch (e: Exception) {
                    status.add("Events Discovery: FAILED (${e.message})")
                }
            } else {
                status.add("Master List: Skipped (Periodic Sync)")
            }

            // 5. Reconcile attendance ONLY for the target event (Unified)
            if (targetEventId != null) {
                eventDao.getEventById(targetEventId)?.let { event ->
                    status.add("Reconciling attendance: ${event.title}")
                    syncAttendanceForEvent(event, scope)
                    status.add("Attendance: OK")
                } ?: run {
                    status.add("Attendance: Skipped (Target event not found)")
                }
            } else {
                status.add("Attendance: Skipped (No event selected)")
            }
            
            android.util.Log.d("AttendanceSync", "Master list sync COMPLETED: ${status.joinToString(", ")}")
            syncLogDao.prune(500)
            return true to status.joinToString("\n")
        } catch (e: Exception) {
            val errorMsg = "Master list sync FAILED:\n${status.joinToString("\n")}"
            android.util.Log.e("AttendanceSync", errorMsg, e)
            return false to errorMsg
        }
    }

    suspend fun syncAttendanceForEvent(
        event: Event, 
        scope: SyncLogScope = NoOpSyncLogScope,
        triggerType: String? = null
    ) {
        val actualScope = if (triggerType != null) {
            DatabaseSyncLogScope(syncLogDao, triggerType)
        } else {
            scope
        }
        
        if (!checkAuthAndRefresh()) {
            actualScope.log("authCheck", false, "Authentication failed")
            return
        }
        
        try {
            // Differential Pull: Fetch rows from M+1 (M=lastProcessedRowIndex)
            val pullResult = cloudProvider.fetchAttendanceForEvent(
                event = event, 
                startIndex = event.lastProcessedRowIndex,
                scope = actualScope
            )
            
            if (pullResult.records.isNotEmpty()) {
                val currentAttendeeIds = attendeeDao.getAllAttendeeIds().toSet()
                val missingAttendeeIds = pullResult.records.map { it.attendeeId }.toSet() - currentAttendeeIds
                
                if (missingAttendeeIds.isNotEmpty()) {
                    android.util.Log.d("AttendanceSync", "Creating ${missingAttendeeIds.size} attendee placeholders from pull result")
                    // Map unique missing IDs back to their latest fullName found in pull records
                    val placeholders = pullResult.records
                        .filter { it.attendeeId in missingAttendeeIds }
                        .associateBy { it.attendeeId }
                        .values
                        .map { record ->
                            Attendee(
                                id = record.attendeeId,
                                fullName = record.fullName,
                                notExistOnCloud = true
                            )
                        }
                    attendeeDao.insertAll(placeholders)
                }
                
                attendanceDao.upsertAllIfNewer(pullResult.records)
                actualScope.log(
                    operation = "pull", 
                    success = true, 
                    params = "event=${event.title} fetched=${pullResult.records.size}"
                )
            }
            
            // Update: Set lastProcessedRowIndex to the cloud's current end index after pull
            if (pullResult.lastRowIndex != event.lastProcessedRowIndex) {
                eventDao.updateLastProcessedRowIndex(event.id, pullResult.lastRowIndex)
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceSync", "Failed to sync attendance for ${event.title}: ${e.message}")
            actualScope.log("pull", false, "Failed to sync attendance", error = e.message, stackTrace = e.stackTraceToString())
        }
    }

    private suspend fun mergeAndInsertEvents(remoteEvents: List<Event>) {
        val localEvents = eventDao.getAllEvents().first()
        val localByCloudId = localEvents.filter { it.cloudEventId != null }.associateBy { it.cloudEventId }
        val localByTitle = localEvents.associateBy { it.title }
        
        val remoteCloudIds = remoteEvents.mapNotNull { it.cloudEventId }.toSet()
        val remoteTitles = remoteEvents.map { it.title }.toSet()

        val mergedEvents = remoteEvents.map { remote ->
            val date = EventSuggester.parseDate(remote.title)?.toString() ?: ""
            val time = remote.title.split(" ").getOrNull(1) ?: ""
            
            val existing = localByCloudId[remote.cloudEventId] ?: localByTitle[remote.title]
            if (existing != null) {
                existing.copy(
                    title = remote.title,
                    date = date,
                    time = time,
                    cloudEventId = remote.cloudEventId,
                    notExistOnCloud = false // Rediscovered
                )
            } else {
                remote.copy(date = date, time = time, notExistOnCloud = false)
            }
        }
        eventDao.insertAll(mergedEvents)

        // Identify local events that are missing from remote (within the same window, e.g., 30 days)
        val cutoff = LocalDate.now().minusDays(30)
        val missingEventIds = localEvents.filter { local ->
            val eventDate = EventSuggester.parseDate(local.title)
            val isWithinWindow = eventDate != null && (eventDate.isAfter(cutoff) || eventDate.isEqual(cutoff))
            
            isWithinWindow && 
            local.cloudEventId !in remoteCloudIds && 
            local.title !in remoteTitles &&
            !local.notExistOnCloud
        }.map { it.id }

        if (missingEventIds.isNotEmpty()) {
            eventDao.markAsMissingOnCloud(missingEventIds)
        }
    }

    suspend fun getUpcomingEvent(oneHourAgo: java.time.LocalDateTime): Event? {
        val date = oneHourAgo.toLocalDate().toString()
        val time = oneHourAgo.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HHmm"))
        return eventDao.getEarliestUpcomingEvent(date, time)
    }

    suspend fun getLatestEvent(): Event? = eventDao.getLatestEvent()

    suspend fun clearAllData() {
        prefs.edit().remove(PREF_MASTER_VERSION).apply()
        attendeeDao.clearAll()
        attendanceDao.clearAll()
        persistentQueueDao.clear()
        syncJobDao.clearAll()
        queueArchiveDao.clearAll()
        eventDao.clearAll()
        groupDao.clearAll()
        attendeeGroupMappingDao.clearAll()
    }

    suspend fun syncRecentEvents(clearFirst: Boolean = false, triggerType: String = "MANUAL") {
        if (!checkAuthAndRefresh()) return

        if (syncJobDao.getPendingCount() > 0) {
            android.util.Log.d("AttendanceSync", "Skipping recent events sync due to pending jobs.")
            return
        }
        
        val scope = DatabaseSyncLogScope(syncLogDao, triggerType)
        try {
            val remoteEvents = cloudProvider.fetchRecentEvents(30, scope)
            if (clearFirst) {
                eventDao.clearAll()
            }
            if (remoteEvents.isNotEmpty()) {
                mergeAndInsertEvents(remoteEvents)
            }
        } catch (e: Exception) {
            android.util.Log.e("AttendanceSync", "Failed to sync recent events: ${e.message}")
        }
    }

    fun getAttendanceRecords(eventId: String): Flow<List<AttendanceRecord>> = 
        attendanceDao.getAttendanceFlow(eventId)

    fun getPendingSyncCount(): Flow<Int> = syncJobDao.getPendingCountFlow()

    fun getOldestPendingEventId(): Flow<String?> = syncJobDao.getOldestSyncJobFlow().map { it?.eventId }

    suspend fun addToQueue(attendeeId: String) {
        val queue = persistentQueueDao.getQueue().first()
        if (queue.none { it.attendeeId == attendeeId }) {
            persistentQueueDao.insertAll(listOf(PersistentQueue(attendeeId)))
        }
    }

    suspend fun restoreFromArchive(archiveId: Long) {
        val archive = queueArchiveDao.getArchiveById(archiveId) ?: return
        
        val regex = Regex("\"id\":\"(.*?)\".*?\"state\":\"(.*?)\"")
        val matches = regex.findAll(archive.dataJson)
        
        val newItems = matches.map { match ->
            val id = match.groupValues[1]
            val state = match.groupValues[2]
            PersistentQueue(id, isLater = (state == "ABSENT"))
        }.toList()

        val currentQueue = persistentQueueDao.getQueue().first()
        val currentIds = currentQueue.map { it.attendeeId }.toSet()
        
        val itemsToAdd = newItems.filter { it.attendeeId !in currentIds }
        persistentQueueDao.insertAll(itemsToAdd)
    }

    suspend fun removeFromQueue(attendeeId: String) {
        persistentQueueDao.remove(attendeeId)
    }

    suspend fun toggleLater(attendeeId: String, later: Boolean) {
        persistentQueueDao.toggleLater(attendeeId, later)
    }

    suspend fun clearQueue() {
        persistentQueueDao.clear()
    }

    suspend fun clearReadyQueue() {
        persistentQueueDao.clearReady()
    }

    suspend fun purgeAllMissingFromCloud() {
        attendeeDao.purgeAllMissingOnCloud()
        groupDao.purgeAllMissingOnCloud()
    }

    fun getMissingOnCloudAttendees(): Flow<List<Attendee>> = attendeeDao.getMissingOnCloudAttendees()
    fun getMissingOnCloudGroups(): Flow<List<Group>> = groupDao.getMissingOnCloudGroups()
    fun getMissingOnCloudEvents(): Flow<List<Event>> = eventDao.getMissingOnCloudEvents()

    suspend fun removeAttendeeById(id: String) = attendeeDao.deleteById(id)
    suspend fun removeGroupById(id: String) = groupDao.deleteById(id)

    suspend fun resolveEventRecreate(eventId: String) {
        val event = eventDao.getEventById(eventId) ?: return
        
        // 1. Ensure worksheet exists and update cloudEventId
        val scope = DatabaseSyncLogScope(syncLogDao, "RESOLUTION")
        val result = cloudProvider.pushAttendance(event, emptyList(), scope, failIfMissing = false)
        
        if (result is PushResult.SuccessWithMapping) {
            eventDao.updateCloudEventId(event.id, result.cloudEventId)
        }
        
        // 2. Clear any pending SyncJobs for this event to avoid duplicates or conflicts
        syncJobDao.deleteJobsForEvent(event.id)
        
        // 3. Clear missing flag
        eventDao.clearMissingOnCloud(event.id)
        
        // 4. Queue a full sync job to push ALL local attendance records for this event
        val localRecords = attendanceDao.getAttendanceForEvent(event.id)
        if (localRecords.isNotEmpty()) {
            val timestamp = timeProvider.now()
            val payload = localRecords.map { record ->
                val escapedName = record.fullName.replace("\"", "\\\"")
                "{\"id\":\"${record.attendeeId}\",\"name\":\"$escapedName\",\"state\":\"${record.state}\",\"time\":${record.timestamp}}"
            }.joinToString(prefix = "[", postfix = "]", separator = ",")
            
            syncJobDao.insert(SyncJob(
                eventId = event.id,
                payloadJson = payload,
                createdAt = timestamp
            ))
        }
        
        retrySync()
    }

    suspend fun resolveEventDeleteLocally(eventId: String) {
        // Clear all pending SyncJobs for this event
        syncJobDao.deleteJobsForEvent(eventId)
        
        // Clear local attendance for this event
        attendanceDao.deleteForEvent(eventId)
        
        // Delete the event from the database
        eventDao.deleteById(eventId)
    }

    suspend fun isAttendeeInUse(attendeeId: String): Boolean {
        return attendanceDao.hasAttendanceForAttendee(attendeeId) || 
               attendeeGroupMappingDao.hasMappingsForAttendee(attendeeId)
    }

    suspend fun isGroupInUse(groupId: String): Boolean {
        return attendeeGroupMappingDao.hasMappingsForGroup(groupId)
    }

    suspend fun replaceQueueWithSelection(attendeeIds: List<String>) {
        val currentQueue = persistentQueueDao.getQueue().first()
        val laterIds = currentQueue.filter { it.isLater }.map { it.attendeeId }.toSet()
        
        if (currentQueue.isNotEmpty()) {
            val timestamp = timeProvider.now()
            val payload = currentQueue.map { q ->
                val state = if (q.isLater) "ABSENT" else "PRESENT"
                "{\"id\":\"${q.attendeeId}\",\"state\":\"$state\",\"time\":$timestamp}"
            }.joinToString(prefix = "[", postfix = "]", separator = ",")
            
            queueArchiveDao.insertWithFifo(QueueArchive(
                eventId = "replaces",
                timestamp = timestamp,
                dataJson = payload
            ))
        }

        val newQueue = attendeeIds.map { id ->
            PersistentQueue(attendeeId = id, isLater = id in laterIds)
        }
        persistentQueueDao.replaceQueue(newQueue)
    }

    fun getAllGroups(): Flow<List<Group>> = groupDao.getAllGroups()

    suspend fun getGroupsForAttendee(attendeeId: String): List<String> = 
        attendeeGroupMappingDao.getGroupsForAttendee(attendeeId)

    fun getAttendeesByGroup(groupId: String): Flow<List<Attendee>> {
        return combine(
            attendeeDao.getAllAttendees(),
            attendeeGroupMappingDao.getAllMappings()
        ) { attendees, mappings ->
            val attendeeIdsInGroup = mappings.filter { it.groupId == groupId }.map { it.attendeeId }.toSet()
            attendees.filter { it.id in attendeeIdsInGroup }
        }
    }

    fun getAllMappings(): Flow<List<AttendeeGroupMapping>> = 
        attendeeGroupMappingDao.getAllMappings()

    fun getAllEvents(): Flow<List<Event>> = eventDao.getAllEvents()

    fun getManageableEvents(): Flow<List<Event>> {
        return getAllEvents().map { events ->
            val cutoff = LocalDate.now().minusDays(30)
            events.filter { event ->
                val eventDate = EventSuggester.parseDate(event.title)
                eventDate != null && (eventDate.isAfter(cutoff) || eventDate.isEqual(cutoff))
            }
        }
    }

    suspend fun getEventById(id: String): Event? = eventDao.getEventById(id)

    suspend fun getEventByTitle(title: String): Event? = eventDao.getEventByTitle(title)

    suspend fun findEventByTitleIgnoreCase(title: String): Event? = eventDao.findEventByTitleIgnoreCase(title)

    suspend fun insertEvent(event: Event) {
        eventDao.insert(event)
        
        val timestamp = timeProvider.now()
        val job = SyncJob(
            eventId = event.id,
            payloadJson = "[]",
            createdAt = timestamp
        )
        syncJobDao.insert(job)
        syncScheduler.scheduleSync()
    }

    suspend fun deleteEvent(id: String) {
        attendanceDao.deleteForEvent(id)
        eventDao.deleteById(id)
    }

    suspend fun purgeOldEvents() {
        val cutoff = LocalDate.now().minusDays(30)
        val allEvents = eventDao.getAllEvents().first()
        allEvents.forEach { event ->
            val eventDate = EventSuggester.parseDate(event.title)
            if (eventDate == null || eventDate.isBefore(cutoff)) {
                deleteEvent(event.id)
            }
        }
    }
}
