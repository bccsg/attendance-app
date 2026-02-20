package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue_archive")
data class QueueArchive(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventTitle: String,
    val timestamp: Long,
    val dataJson: String // Blob containing the list of IDs and states
)
