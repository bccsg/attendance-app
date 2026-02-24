package sg.org.bcc.attendance.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import sg.org.bcc.attendance.data.local.entities.SyncLog

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(log: SyncLog)

    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SyncLog>>

    @Query("SELECT DISTINCT triggerId, triggerType, MIN(timestamp) as startTime FROM sync_logs GROUP BY triggerId ORDER BY startTime DESC")
    fun getTriggersSummary(): Flow<List<TriggerSummary>>

    @Query("SELECT * FROM sync_logs WHERE triggerId = :triggerId ORDER BY timestamp ASC")
    fun getLogsForTrigger(triggerId: String): Flow<List<SyncLog>>

    @Query("DELETE FROM sync_logs WHERE id NOT IN (SELECT id FROM sync_logs ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM sync_logs")
    suspend fun clearAll()
}

data class TriggerSummary(
    val triggerId: String,
    val triggerType: String,
    val startTime: Long
)
