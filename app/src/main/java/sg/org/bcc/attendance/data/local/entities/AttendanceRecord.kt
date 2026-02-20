package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity

@Entity(
    tableName = "attendance_records",
    primaryKeys = ["eventTitle", "attendeeId"]
)
data class AttendanceRecord(
    val eventTitle: String,
    val attendeeId: String,
    val state: String, // PRESENT or ABSENT
    val timestamp: Long
)
