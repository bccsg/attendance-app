package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "attendee_group_mapping",
    primaryKeys = ["attendeeId", "groupId"]
)
data class AttendeeGroupMapping(
    val attendeeId: String,
    val groupId: String
)
