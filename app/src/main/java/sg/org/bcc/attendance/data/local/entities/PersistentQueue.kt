package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persistent_queue")
data class PersistentQueue(
    @PrimaryKey val attendeeId: String,
    val isLater: Boolean = false
)
