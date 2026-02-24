package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "events")
data class Event(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String, // Full title: "yyMMdd HHmm Name"
    val date: String,  // ISO-8601 date: "yyyy-MM-dd"
    val time: String,  // 24h time: "HHmm"
    val cloudEventId: String? = null,
    val lastSyncTime: Long? = null,
    val lastProcessedRowIndex: Int = 0
)
