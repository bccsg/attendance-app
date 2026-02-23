package sg.org.bcc.attendance.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import sg.org.bcc.attendance.data.local.dao.*
import sg.org.bcc.attendance.data.local.entities.*
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.data.remote.AuthManager
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
    private val attendeeDao: AttendeeDao,
    private val attendanceDao: AttendanceDao,
    private val persistentQueueDao: PersistentQueueDao,
    private val syncJobDao: SyncJobDao,
    private val queueArchiveDao: QueueArchiveDao,
    private val eventDao: EventDao,
    private val groupDao: GroupDao,
    private val attendeeGroupMappingDao: AttendeeGroupMappingDao,
    private val cloudProvider: AttendanceCloudProvider,
    private val authManager: AuthManager,
    private val timeProvider: TimeProvider
) {
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

        val payload = readyItems.map { q ->
            "{\"id\":\"${q.attendeeId}\",\"state\":\"$state\",\"time\":$timestamp}"
        }.joinToString(prefix = "[", postfix = "]", separator = ",")

        // Local Write: Update local attendance state immediately for UI feedback
        val records = readyItems.map { q ->
            AttendanceRecord(
                eventId = eventId,
                attendeeId = q.attendeeId,
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
    }

    fun getArchives(): Flow<List<QueueArchive>> = queueArchiveDao.getAllArchives()

    fun getAllAttendees(): Flow<List<Attendee>> = attendeeDao.getAllAttendees()

    suspend fun isDemoMode(): Boolean {
        // Demo mode is active when the user is not authenticated.
        return !authManager.isAuthed.value
    }

    suspend fun syncMasterList() {
        val remoteAttendees = cloudProvider.fetchMasterAttendees()
        if (remoteAttendees.isNotEmpty()) {
            attendeeDao.clearAll()
            attendeeDao.insertAll(remoteAttendees)
            
            // Sync groups
            val remoteGroups = cloudProvider.fetchMasterGroups()
            groupDao.clearAll()
            groupDao.insertAll(remoteGroups)

            // Sync mappings
            val remoteMappings = cloudProvider.fetchAttendeeGroupMappings()
            attendeeGroupMappingDao.clearAll()
            attendeeGroupMappingDao.insertAll(remoteMappings)

            // Re-sync events
            syncRecentEvents()
            
            // Clear transient states
            persistentQueueDao.clear()
            syncJobDao.clearAll()
            attendanceDao.clearAll()
        }
    }

    suspend fun clearAllData() {
        attendeeDao.clearAll()
        attendanceDao.clearAll()
        persistentQueueDao.clear()
        syncJobDao.clearAll()
        queueArchiveDao.clearAll()
        eventDao.clearAll()
        groupDao.clearAll()
        attendeeGroupMappingDao.clearAll()
    }

    suspend fun syncRecentEvents() {
        val recentEvents = cloudProvider.fetchRecentEvents(30)
        if (recentEvents.isNotEmpty()) {
            eventDao.insertAll(recentEvents)
        }
    }

    fun getAttendanceRecords(eventId: String): Flow<List<AttendanceRecord>> = 
        attendanceDao.getAttendanceFlow(eventId)

    fun getPendingSyncCount(): Flow<Int> = syncJobDao.getPendingCountFlow()

    suspend fun addToQueue(attendeeId: String) {
        val queue = persistentQueueDao.getQueue().first()
        if (queue.none { it.attendeeId == attendeeId }) {
            persistentQueueDao.insertAll(listOf(PersistentQueue(attendeeId)))
        }
    }

    suspend fun restoreFromArchive(archiveId: Long) {
        val archive = queueArchiveDao.getArchiveById(archiveId) ?: return
        
        // Simple JSON parsing (in a real app, use Gson/Moshi)
        val regex = Regex("\"id\":\"(.*?)\".*?\"state\":\"(.*?)\"")
        val matches = regex.findAll(archive.dataJson)
        
        val newItems = matches.map { match ->
            val id = match.groupValues[1]
            val state = match.groupValues[2]
            PersistentQueue(id, isLater = (state == "ABSENT"))
        }.toList()

        // Append to current queue
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

    suspend fun replaceQueueWithSelection(attendeeIds: List<String>) {
        val currentQueue = persistentQueueDao.getQueue().first()
        val laterIds = currentQueue.filter { it.isLater }.map { it.attendeeId }.toSet()
        
        // Archive the old queue before replacing
        if (currentQueue.isNotEmpty()) {
            val timestamp = timeProvider.now()
            val payload = currentQueue.map { q ->
                val state = if (q.isLater) "ABSENT" else "PRESENT"
                "{\"id\":\"${q.attendeeId}\",\"state\":\"$state\",\"time\":$timestamp}"
            }.joinToString(prefix = "[", postfix = "]", separator = ",")
            
            queueArchiveDao.insertWithFifo(QueueArchive(
                eventId = "replaces", // Use a generic ID or actual event ID if available
                timestamp = timestamp,
                dataJson = payload
            ))
        }

        val newQueue = attendeeIds.map { id ->
            PersistentQueue(attendeeId = id, isLater = id in laterIds)
        }
        persistentQueueDao.replaceQueue(newQueue)
    }

    // Group Management functions
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

    // Event Management functions
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

    suspend fun insertEvent(event: Event) = eventDao.insert(event)

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
