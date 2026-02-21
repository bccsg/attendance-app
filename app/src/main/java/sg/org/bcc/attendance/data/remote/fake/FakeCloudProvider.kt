package sg.org.bcc.attendance.data.remote.fake

import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import javax.inject.Inject

class FakeCloudProvider @Inject constructor() : AttendanceCloudProvider {
    override suspend fun pushAttendance(eventTitle: String, records: List<AttendanceRecord>): Boolean = true

    override suspend fun fetchMasterAttendees(): List<Attendee> {
        // Real data to replace demo data
        return listOf(
            Attendee("R01", "Real User 1", "Real1"),
            Attendee("R02", "Real User 2", "Real2")
        )
    }

    override suspend fun fetchMasterGroups(): List<Group> = emptyList()

    override suspend fun fetchAttendanceForEvent(eventTitle: String): List<AttendanceRecord> = emptyList()
}
