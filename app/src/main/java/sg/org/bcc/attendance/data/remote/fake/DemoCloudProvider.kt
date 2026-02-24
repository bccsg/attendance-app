package sg.org.bcc.attendance.data.remote.fake

import sg.org.bcc.attendance.data.local.dao.AttendanceDao
import sg.org.bcc.attendance.data.local.dao.EventDao
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendeeGroupMapping
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.data.remote.PushResult
import sg.org.bcc.attendance.sync.SyncLogScope
import sg.org.bcc.attendance.util.DemoData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class DemoCloudProvider @Inject constructor(
    private val eventDao: EventDao,
    private val attendanceDao: AttendanceDao
) : AttendanceCloudProvider {
    
    // In-memory "cloud" state for the demo session to simulate real-time push/pull
    // This allows immediate feedback without waiting for Room's asynchronous cycles in some cases
    private val sessionPushedRecords = mutableMapOf<String, MutableList<AttendanceRecord>>()

    override suspend fun pushAttendance(
        event: Event, 
        records: List<AttendanceRecord>,
        scope: SyncLogScope
    ): PushResult {
        val params = "event='${event.title}', records=${records.size}"
        try {
            val eventRecords = sessionPushedRecords.getOrPut(event.id) { mutableListOf() }
            records.forEach { newRecord ->
                eventRecords.removeIf { it.attendeeId == newRecord.attendeeId }
                eventRecords.add(newRecord)
            }
            
            // Simulate formatting for log transparency
            val sgtZone = java.time.ZoneId.of("Asia/Singapore")
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            records.forEach { 
                val formatted = java.time.Instant.ofEpochMilli(it.timestamp).atZone(sgtZone).format(formatter)
                android.util.Log.d("DemoCloud", "Pushed record for ${it.attendeeId} at $formatted")
            }
            
            val result = if (event.cloudEventId == null) {
                PushResult.SuccessWithMapping("sheet-${event.id.take(4)}")
            } else {
                PushResult.Success
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
        scope: SyncLogScope
    ): List<AttendanceRecord> {
        val params = "event='${event.title}'"
        return try {
            // Preference: 1. Current session memory, 2. Local Database
            val sessionRecords = sessionPushedRecords[event.id]
            if (sessionRecords != null) {
                scope.log("fetchAttendanceForEvent", true, params = params)
                return sessionRecords
            }
            
            attendanceDao.getAttendanceForEvent(event.id).also {
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
