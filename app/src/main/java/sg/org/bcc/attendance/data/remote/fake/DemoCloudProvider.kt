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

    override suspend fun pushAttendance(event: Event, records: List<AttendanceRecord>): PushResult {
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
        
        return if (event.cloudEventId == null) {
            PushResult.SuccessWithMapping("sheet-${event.id.take(4)}")
        } else {
            PushResult.Success
        }
    }

    override suspend fun fetchMasterAttendees(): List<Attendee> = DemoData.disneyCharacters

    override suspend fun fetchMasterGroups(): List<Group> = DemoData.groups

    override suspend fun fetchAttendeeGroupMappings(): List<AttendeeGroupMapping> = DemoData.mappings

    override suspend fun fetchAttendanceForEvent(event: Event): List<AttendanceRecord> {
        // Preference: 1. Current session memory, 2. Local Database
        val sessionRecords = sessionPushedRecords[event.id]
        if (sessionRecords != null) return sessionRecords
        
        return attendanceDao.getAttendanceForEvent(event.id)
    }

    override suspend fun fetchRecentEvents(days: Int): List<Event> {
        val localEvents = eventDao.getAllEvents().first()
        if (localEvents.isNotEmpty()) {
            return localEvents
        }
        return DemoData.generateRecentEvents(days)
    }
}
