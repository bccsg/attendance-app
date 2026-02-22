package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "events")
data class Event(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String, // Format: yyMMdd HHmm Name
    val cloudEventId: String? = null,
    val lastSyncTime: Long? = null
)
