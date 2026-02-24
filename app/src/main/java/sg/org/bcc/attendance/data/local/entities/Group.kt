package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey val groupId: String, // Group name acts as ID
    val name: String,
    val notExistOnCloud: Boolean = false
)
