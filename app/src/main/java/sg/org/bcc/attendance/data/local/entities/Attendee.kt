package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendees")
data class Attendee(
    @PrimaryKey val id: String,
    val fullName: String,
    val shortName: String? = null,
    val notExistOnCloud: Boolean = false
)
