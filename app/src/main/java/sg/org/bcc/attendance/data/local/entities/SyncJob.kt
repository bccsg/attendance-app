package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_jobs")
data class SyncJob(
    @PrimaryKey(autoGenerate = true) val jobId: Long = 0,
    val eventId: String, // UUID of the event
    val payloadJson: String, // List of {attendee_id, state, timestamp}
    val createdAt: Long = System.currentTimeMillis()
)
