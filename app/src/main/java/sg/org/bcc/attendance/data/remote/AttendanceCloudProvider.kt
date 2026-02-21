package sg.org.bcc.attendance.data.remote

import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.Group

interface AttendanceCloudProvider {
    /**
     * Push a batch of attendance records to the cloud.
     * @return true if successful, false otherwise.
     */
    suspend fun pushAttendance(eventTitle: String, records: List<AttendanceRecord>): Boolean

    /**
     * Fetch the master list of attendees.
     */
    suspend fun fetchMasterAttendees(): List<Attendee>

    /**
     * Fetch the master list of groups.
     */
    suspend fun fetchMasterGroups(): List<Group>

    /**
     * Fetch remote attendance state for an event to reconcile.
     */
    suspend fun fetchAttendanceForEvent(eventTitle: String): List<AttendanceRecord>
}
