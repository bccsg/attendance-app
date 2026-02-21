package sg.org.bcc.attendance.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import sg.org.bcc.attendance.data.local.dao.AttendeeDao
import sg.org.bcc.attendance.data.local.dao.AttendanceDao
import sg.org.bcc.attendance.data.local.dao.PersistentQueueDao
import sg.org.bcc.attendance.data.local.dao.SyncJobDao
import sg.org.bcc.attendance.data.local.dao.QueueArchiveDao
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.PersistentQueue
import sg.org.bcc.attendance.data.local.entities.SyncJob
import sg.org.bcc.attendance.data.local.entities.QueueArchive
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.util.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

data class QueueItem(
    val attendee: Attendee,
    val isExcluded: Boolean
)

@Singleton
class AttendanceRepository @Inject constructor(
    private val attendeeDao: AttendeeDao,
    private val attendanceDao: AttendanceDao,
    private val persistentQueueDao: PersistentQueueDao,
    private val syncJobDao: SyncJobDao,
    private val queueArchiveDao: QueueArchiveDao,
    private val cloudProvider: AttendanceCloudProvider,
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
                    QueueItem(attendee, q.isExcluded)
                }
            }
        }
    }

    suspend fun syncQueue(eventTitle: String, state: String = "PRESENT") {
        val currentQueue = persistentQueueDao.getQueue().first()
        if (currentQueue.isEmpty()) return

        val timestamp = timeProvider.now()
        val includedItems = currentQueue.filter { !it.isExcluded }
        if (includedItems.isEmpty()) return

        val payload = includedItems.map { q ->
            "{\"id\":\"${q.attendeeId}\",\"state\":\"$state\",\"time\":$timestamp}"
        }.joinToString(prefix = "[", postfix = "]", separator = ",")

        // Local Write: Update local attendance state immediately for UI feedback
        val records = includedItems.map { q ->
            AttendanceRecord(
                eventTitle = eventTitle,
                attendeeId = q.attendeeId,
                state = state,
                timestamp = timestamp
            )
        }
        attendanceDao.upsertAllIfNewer(records)

        // In demo mode, we archive but do NOT create a SyncJob
        if (!isDemoMode()) {
            val job = SyncJob(
                eventTitle = eventTitle,
                payloadJson = payload,
                createdAt = timestamp
            )
            syncJobDao.insert(job)
        }

        // Archive before clearing (only archive what was committed)
        val archive = QueueArchive(
            eventTitle = eventTitle,
            timestamp = timestamp,
            dataJson = payload
        )
        queueArchiveDao.insertWithFifo(archive)

        // Only clear the items that were actually committed
        persistentQueueDao.clearActive()
    }

    fun getArchives(): Flow<List<QueueArchive>> = queueArchiveDao.getAllArchives()

    fun getAllAttendees(): Flow<List<Attendee>> = attendeeDao.getAllAttendees()

    suspend fun isDemoMode(): Boolean {
        return attendeeDao.getAllAttendees().first().any { it.id.startsWith("D") }
    }

    suspend fun syncMasterList() {
        val remoteAttendees = cloudProvider.fetchMasterAttendees()
        if (remoteAttendees.isNotEmpty()) {
            attendeeDao.clearAll()
            attendeeDao.insertAll(remoteAttendees)
            
            // Clear demo-related state
            persistentQueueDao.clear()
            syncJobDao.clearAll()
            attendanceDao.clearAll()
        }
    }

    fun getAttendanceRecords(eventTitle: String): Flow<List<AttendanceRecord>> = 
        attendanceDao.getAttendanceFlow(eventTitle)

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
            PersistentQueue(id, isExcluded = (state == "ABSENT"))
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

    suspend fun toggleExclusion(attendeeId: String, excluded: Boolean) {
        persistentQueueDao.toggleExclusion(attendeeId, excluded)
    }

    suspend fun clearQueue() {
        persistentQueueDao.clear()
    }

    suspend fun clearActiveQueue() {
        persistentQueueDao.clearActive()
    }

    suspend fun replaceQueueWithSelection(attendeeIds: List<String>) {
        val currentQueue = persistentQueueDao.getQueue().first()
        val excludedIds = currentQueue.filter { it.isExcluded }.map { it.attendeeId }.toSet()
        
        // Archive the old queue before replacing
        if (currentQueue.isNotEmpty()) {
            val timestamp = timeProvider.now()
            val payload = currentQueue.map { q ->
                val state = if (q.isExcluded) "ABSENT" else "PRESENT"
                "{\"id\":\"${q.attendeeId}\",\"state\":\"$state\",\"time\":$timestamp}"
            }.joinToString(prefix = "[", postfix = "]", separator = ",")
            
            queueArchiveDao.insertWithFifo(QueueArchive(
                eventTitle = "Archived Queue", // Generic title for explicit replaces
                timestamp = timestamp,
                dataJson = payload
            ))
        }

        val newQueue = attendeeIds.map { id ->
            PersistentQueue(attendeeId = id, isExcluded = id in excludedIds)
        }
        persistentQueueDao.replaceQueue(newQueue)
    }
}
