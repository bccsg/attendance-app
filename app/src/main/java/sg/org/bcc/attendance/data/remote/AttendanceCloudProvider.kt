package sg.org.bcc.attendance.data.remote

import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendeeGroupMapping
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.local.entities.Group

sealed class PushResult {
    data object Success : PushResult()
    data class SuccessWithMapping(val cloudEventId: String) : PushResult()
    data class Error(val message: String, val isRetryable: Boolean) : PushResult()
}

interface AttendanceCloudProvider {
    /**
     * Push a batch of attendance records to the cloud.
     * Implementation should match by [Event.cloudEventId] or [Event.title].
     */
    suspend fun pushAttendance(event: Event, records: List<AttendanceRecord>): PushResult

    /**
     * Fetch the master list of attendees.
     */
    suspend fun fetchMasterAttendees(): List<Attendee>

    /**
     * Fetch the master list of groups.
     */
    suspend fun fetchMasterGroups(): List<Group>

    /**
     * Fetch the master list of attendee group mappings.
     */
    suspend fun fetchAttendeeGroupMappings(): List<AttendeeGroupMapping>

    /**
     * Fetch remote attendance state for an event to reconcile.
     */
    suspend fun fetchAttendanceForEvent(event: Event): List<AttendanceRecord>

    /**
     * Fetch events occurring within the last [days].
     */
    suspend fun fetchRecentEvents(days: Int): List<Event>
}
