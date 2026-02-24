package sg.org.bcc.attendance.data.remote

import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendeeGroupMapping
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.data.remote.fake.DemoCloudProvider
import sg.org.bcc.attendance.sync.SyncLogScope
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

    override suspend fun pushAttendance(
        event: Event, 
        records: List<AttendanceRecord>,
        scope: SyncLogScope
    ): PushResult {
        return activeProvider.pushAttendance(event, records, scope)
    }

    override suspend fun fetchMasterAttendees(scope: SyncLogScope): List<Attendee> {
        return activeProvider.fetchMasterAttendees(scope)
    }

    override suspend fun fetchMasterGroups(scope: SyncLogScope): List<Group> {
        return activeProvider.fetchMasterGroups(scope)
    }

    override suspend fun fetchAttendeeGroupMappings(scope: SyncLogScope): List<AttendeeGroupMapping> {
        return activeProvider.fetchAttendeeGroupMappings(scope)
    }

    override suspend fun fetchMasterListVersion(scope: SyncLogScope): String {
        return activeProvider.fetchMasterListVersion(scope)
    }

    override suspend fun fetchAttendanceForEvent(
        event: Event,
        scope: SyncLogScope
    ): List<AttendanceRecord> {
        return activeProvider.fetchAttendanceForEvent(event, scope)
    }

    override suspend fun fetchRecentEvents(
        days: Int,
        scope: SyncLogScope
    ): List<Event> {
        return activeProvider.fetchRecentEvents(days, scope)
    }
}
