package sg.org.bcc.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import sg.org.bcc.attendance.data.local.entities.SyncJob

@Dao
interface SyncJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: SyncJob)

    @Query("SELECT * FROM sync_jobs ORDER BY createdAt ASC LIMIT 1")
    suspend fun getOldestSyncJob(): SyncJob?

    @Query("DELETE FROM sync_jobs WHERE jobId = :jobId")
    suspend fun deleteJob(jobId: Long)

    @Query("SELECT count(*) FROM sync_jobs")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT count(*) FROM sync_jobs")
    suspend fun getPendingCount(): Int

    @Query("DELETE FROM sync_jobs WHERE eventId = :eventId")
    suspend fun deleteJobsForEvent(eventId: String)

    @Query("DELETE FROM sync_jobs")
    suspend fun clearAll()
}
