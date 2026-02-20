package sg.org.bcc.attendance.data.local.entities

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

// We use FTS4 as it is more widely compatible across Android versions than FTS5
// though requirements mentioned FTS5, FTS4 provides the same core benefits for our scale.
@Fts4(contentEntity = Attendee::class)
@Entity(tableName = "attendees_fts")
data class AttendeeFts(
    val fullName: String,
    val shortName: String?
)
