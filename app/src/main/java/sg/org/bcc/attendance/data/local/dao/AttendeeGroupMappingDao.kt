package sg.org.bcc.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import sg.org.bcc.attendance.data.local.entities.AttendeeGroupMapping
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendeeGroupMappingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<AttendeeGroupMapping>)

    @Query("SELECT * FROM attendee_group_mapping")
    fun getAllMappings(): Flow<List<AttendeeGroupMapping>>

    @Query("SELECT groupId FROM attendee_group_mapping WHERE attendeeId = :attendeeId")
    suspend fun getGroupsForAttendee(attendeeId: String): List<String>

    @Query("DELETE FROM attendee_group_mapping")
    suspend fun clearAll()
}
