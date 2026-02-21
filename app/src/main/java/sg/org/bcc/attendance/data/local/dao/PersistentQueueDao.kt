package sg.org.bcc.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import sg.org.bcc.attendance.data.local.entities.PersistentQueue

@Dao
interface PersistentQueueDao {
    @Query("SELECT * FROM persistent_queue")
    fun getQueue(): Flow<List<PersistentQueue>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PersistentQueue>)

    @Query("DELETE FROM persistent_queue")
    suspend fun clear()

    @Query("DELETE FROM persistent_queue WHERE isExcluded = 0")
    suspend fun clearActive()

    @Transaction
    suspend fun replaceQueue(items: List<PersistentQueue>) {
        clear()
        insertAll(items)
    }

    @Query("DELETE FROM persistent_queue WHERE attendeeId = :attendeeId")
    suspend fun remove(attendeeId: String)

    @Query("UPDATE persistent_queue SET isExcluded = :excluded WHERE attendeeId = :attendeeId")
    suspend fun toggleExclusion(attendeeId: String, excluded: Boolean)
}
