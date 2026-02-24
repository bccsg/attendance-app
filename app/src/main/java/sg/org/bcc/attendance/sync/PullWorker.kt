package sg.org.bcc.attendance.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.data.local.dao.SyncJobDao

@HiltWorker
class PullWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: AttendanceRepository,
    private val syncJobDao: SyncJobDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val PROGRESS_OP = "progress_op"
        const val PROGRESS_STATE = "progress_state"
        const val PROGRESS_ERROR = "progress_error"
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        
        // Integrity Protection: Disable remote pulls while sync_jobs are pending 
        // to prevent stale data overwrites.
        if (syncJobDao.getPendingCount() > 0) {
            prefs.edit().putString("last_pull_status", "Skipped (pending pushes)").apply()
            return Result.success()
        }

        prefs.edit().putString("last_pull_status", "Syncing...").apply()
        
        setProgress(workDataOf(
            PROGRESS_OP to "Pulling Events",
            PROGRESS_STATE to "SYNCING"
        ))

        val appPrefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val selectedEventId = appPrefs.getString("selected_event_id", null)

        return try {
            val (success, status) = repository.syncMasterListWithDetailedResult(
                isFullSync = false,
                triggerType = "SCHEDULED",
                targetEventId = selectedEventId
            )
            val now = System.currentTimeMillis()
            if (success) {
                prefs.edit().putLong("last_pull_time", now).putString("last_pull_status", "Success").apply()
                setProgress(workDataOf(
                    PROGRESS_STATE to "IDLE"
                ))
                Result.success()
            } else {
                prefs.edit().putString("last_pull_status", "Failed: $status").apply()
                setProgress(workDataOf(
                    PROGRESS_STATE to "ERROR",
                    PROGRESS_ERROR to status
                ))
                // If it failed due to auth, we shouldn't retry indefinitely
                if (status.contains("Authentication failed", ignoreCase = true)) {
                    Result.failure(workDataOf(PROGRESS_ERROR to status))
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            prefs.edit().putString("last_pull_status", "Error: $errorMsg").apply()
            setProgress(workDataOf(
                PROGRESS_STATE to "ERROR",
                PROGRESS_ERROR to errorMsg
            ))
            Result.retry()
        }
    }
}
