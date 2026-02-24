package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val triggerId: String, // Groups operations in one sync session
    val triggerType: String, // MANUAL, AUTO, LOGIN, etc.
    val operation: String, // fetchMasterAttendees, pushAttendance, etc.
    val params: String? = null, // Scalar arguments passed
    val status: String, // SUCCESS, FAILED
    val errorMessage: String? = null,
    val stackTrace: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
