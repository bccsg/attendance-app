package sg.org.bcc.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import sg.org.bcc.attendance.data.local.entities.QueueArchive

@Dao
interface QueueArchiveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(archive: QueueArchive)

    @Query("SELECT * FROM queue_archive ORDER BY timestamp DESC")
    fun getAllArchives(): Flow<List<QueueArchive>>

    @Query("SELECT count(*) FROM queue_archive")
    suspend fun getCount(): Int

    @Query("DELETE FROM queue_archive WHERE id IN (SELECT id FROM queue_archive ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)

    @Transaction
    suspend fun insertWithFifo(archive: QueueArchive) {
        insert(archive)
        val currentCount = getCount()
        if (currentCount > 25) {
            deleteOldest(currentCount - 25)
        }
    }
    
    @Query("SELECT * FROM queue_archive WHERE id = :id")
    suspend fun getArchiveById(id: Long): QueueArchive?

    @Query("DELETE FROM queue_archive")
    suspend fun clearAll()
}
