package sg.org.bcc.attendance.data.remote.fake

import sg.org.bcc.attendance.data.local.dao.AttendanceDao
import sg.org.bcc.attendance.data.local.dao.EventDao
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendeeGroupMapping
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.data.remote.PullResult
import sg.org.bcc.attendance.data.remote.PushResult
import sg.org.bcc.attendance.sync.SyncLogScope
import sg.org.bcc.attendance.util.DemoData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Singleton
class DemoCloudProvider @Inject constructor(
    private val eventDao: EventDao,
    private val attendanceDao: AttendanceDao
) : AttendanceCloudProvider {

    override val isSyncing: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val syncMessages: Flow<String> = emptyFlow()
    
    // In-memory "cloud" state for the demo session to simulate real-time push/pull
    // This allows immediate feedback without waiting for Room's asynchronous cycles in some cases
    private val sessionPushedRecords = mutableMapOf<String, MutableList<AttendanceRecord>>()

    override suspend fun pushAttendance(
        event: Event, 
        records: List<AttendanceRecord>,
        scope: SyncLogScope,
        failIfMissing: Boolean
    ): PushResult {
        val params = "event='${event.title}', records=${records.size}, failIfMissing=$failIfMissing"
        try {
            val eventRecords = sessionPushedRecords.getOrPut(event.id) { mutableListOf() }
            records.forEach { newRecord ->
                eventRecords.add(newRecord)
            }
            
            // Simulate formatting for log transparency
            val sgtZone = java.time.ZoneId.of("Asia/Singapore")
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            records.forEach { 
                val formatted = java.time.Instant.ofEpochMilli(it.timestamp).atZone(sgtZone).format(formatter)
                android.util.Log.d("DemoCloud", "Pushed record for ${it.attendeeId} at $formatted")
            }
            
            val lastRowIndex = eventRecords.size
            val result = if (event.cloudEventId == null) {
                PushResult.SuccessWithMapping("sheet-${event.id.take(4)}", lastRowIndex)
            } else {
                PushResult.Success(lastRowIndex)
            }
            scope.log("pushAttendance", true, params = params)
            return result
        } catch (e: Exception) {
            scope.log("pushAttendance", false, params = params, error = e.message, stackTrace = e.stackTraceToString())
            throw e
        }
    }

    override suspend fun fetchMasterAttendees(scope: SyncLogScope): List<Attendee> {
        return try {
            DemoData.disneyCharacters.also { scope.log("fetchMasterAttendees", true) }
        } catch (e: Exception) {
            scope.log("fetchMasterAttendees", false, e.message, e.stackTraceToString())
            throw e
        }
    }

    override suspend fun fetchMasterGroups(scope: SyncLogScope): List<Group> {
        return try {
            DemoData.groups.also { scope.log("fetchMasterGroups", true) }
        } catch (e: Exception) {
            scope.log("fetchMasterGroups", false, e.message, e.stackTraceToString())
            throw e
        }
    }

    override suspend fun fetchAttendeeGroupMappings(scope: SyncLogScope): List<AttendeeGroupMapping> {
        return try {
            DemoData.mappings.also { scope.log("fetchAttendeeGroupMappings", true) }
        } catch (e: Exception) {
            scope.log("fetchAttendeeGroupMappings", false, e.message, e.stackTraceToString())
            throw e
        }
    }

    override suspend fun fetchMasterListVersion(scope: SyncLogScope): String {
        return try {
            "demo-version-1".also { scope.log("fetchMasterListVersion", true) }
        } catch (e: Exception) {
            scope.log("fetchMasterListVersion", false, e.message, e.stackTraceToString())
            throw e
        }
    }

    override suspend fun fetchAttendanceForEvent(
        event: Event,
        startIndex: Int,
        scope: SyncLogScope
    ): PullResult {
        val params = "event='${event.title}', start=$startIndex"
        return try {
            // Preference: 1. Current session memory, 2. Local Database
            val sessionRecords = sessionPushedRecords[event.id]
            val records = if (sessionRecords != null) {
                if (startIndex < sessionRecords.size) {
                    sessionRecords.subList(startIndex, sessionRecords.size)
                } else {
                    emptyList()
                }
            } else {
                // For demo purposes, we don't have row indices in DB records easily
                // So if we pull from DB, we assume it's a full sync or already caught up
                emptyList()
            }
            
            val lastRowIndex = (sessionRecords?.size ?: 0).coerceAtLeast(startIndex)
            PullResult(records, lastRowIndex).also {
                scope.log("fetchAttendanceForEvent", true, params = params)
            }
        } catch (e: Exception) {
            scope.log("fetchAttendanceForEvent", false, params = params, error = e.message, stackTrace = e.stackTraceToString())
            throw e
        }
    }

    override suspend fun fetchRecentEvents(days: Int, scope: SyncLogScope): List<Event> {
        val params = "days=$days"
        return try {
            val localEvents = eventDao.getAllEvents().first()
            if (localEvents.isNotEmpty()) {
                scope.log("fetchRecentEvents", true, params = params)
                return localEvents
            }
            DemoData.generateRecentEvents(days).also {
                scope.log("fetchRecentEvents", true, params = params)
            }
        } catch (e: Exception) {
            scope.log("fetchRecentEvents", false, params = params, error = e.message, stackTrace = e.stackTraceToString())
            throw e
        }
    }
}
