package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "attendance_records",
    primaryKeys = ["eventId", "attendeeId"]
)
data class AttendanceRecord(
    val eventId: String, // UUID of the event
    val attendeeId: String,
    val state: String, // PRESENT or ABSENT
    val timestamp: Long
)
