package sg.org.bcc.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import sg.org.bcc.attendance.data.local.entities.Event

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY title DESC")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: String): Event?

    @Query("SELECT * FROM events WHERE title = :title")
    suspend fun getEventByTitle(title: String): Event?

    @Query("SELECT * FROM events WHERE LOWER(title) = LOWER(:title) LIMIT 1")
    suspend fun findEventByTitleIgnoreCase(title: String): Event?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: Event)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<Event>)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM events")
    suspend fun clearAll()
}
