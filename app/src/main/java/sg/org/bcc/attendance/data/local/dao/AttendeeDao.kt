package sg.org.bcc.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import sg.org.bcc.attendance.data.local.entities.Attendee
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attendees: List<Attendee>)

    @Query("SELECT * FROM attendees ORDER BY shortName ASC, fullName ASC")
    fun getAllAttendees(): Flow<List<Attendee>>

    @Query("""
        SELECT * FROM attendees 
        JOIN attendees_fts ON attendees.fullName = attendees_fts.fullName
        WHERE attendees_fts MATCH :query
    """)
    suspend fun searchAttendees(query: String): List<Attendee>

    @Query("SELECT * FROM attendees WHERE id = :id")
    suspend fun getAttendeeById(id: String): Attendee?
}
