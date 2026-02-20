package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class Event(
    @PrimaryKey val title: String, // Format: yymmddhhmm:name
    val lastSyncTime: Long? = null
)
