package sg.org.bcc.attendance.data.remote

import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendeeGroupMapping
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.data.remote.fake.DemoCloudProvider
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class DelegatingCloudProvider @Inject constructor(
    private val authManager: AuthManager,
    private val demoProvider: Provider<DemoCloudProvider>,
    private val gsheetsProvider: Provider<GoogleSheetsAdapter>
) : AttendanceCloudProvider {

    private val activeProvider: AttendanceCloudProvider
        get() = if (authManager.isAuthed.value) {
            gsheetsProvider.get()
        } else {
            demoProvider.get()
        }

    override suspend fun pushAttendance(event: Event, records: List<AttendanceRecord>): PushResult {
        return activeProvider.pushAttendance(event, records)
    }

    override suspend fun fetchMasterAttendees(): List<Attendee> {
        return activeProvider.fetchMasterAttendees()
    }

    override suspend fun fetchMasterGroups(): List<Group> {
        return activeProvider.fetchMasterGroups()
    }

    override suspend fun fetchAttendeeGroupMappings(): List<AttendeeGroupMapping> {
        return activeProvider.fetchAttendeeGroupMappings()
    }

    override suspend fun fetchAttendanceForEvent(event: Event): List<AttendanceRecord> {
        return activeProvider.fetchAttendanceForEvent(event)
    }

    override suspend fun fetchRecentEvents(days: Int): List<Event> {
        return activeProvider.fetchRecentEvents(days)
    }
}
