package sg.org.bcc.attendance.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import sg.org.bcc.attendance.data.local.dao.SyncJobDao
import sg.org.bcc.attendance.data.local.dao.EventDao
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.data.remote.PushResult

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncJobDao: SyncJobDao,
    private val eventDao: EventDao,
    private val cloudProvider: AttendanceCloudProvider,
    private val authManager: AuthManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // 1. Check Auth & Refresh if needed
        if (authManager.isTokenExpired()) {
            val refreshed = authManager.silentRefresh()
            if (!refreshed) {
                // Cannot sync without valid token, retry later (WorkManager will back off)
                return Result.retry()
            }
        }

        // 2. Process jobs sequentially
        while (true) {
            val job = syncJobDao.getOldestSyncJob() ?: break
            
            val event = eventDao.getEventById(job.eventId)
            if (event == null) {
                // Should not happen, but if event is gone, we can't push.
                // Discard job to prevent blocking the queue.
                syncJobDao.deleteJob(job.jobId)
                continue
            }

            val records = parsePayload(job.eventId, job.payloadJson)
            
            val result = try {
                cloudProvider.pushAttendance(event, records)
            } catch (e: Exception) {
                PushResult.Error(e.message ?: "Unknown error", isRetryable = true)
            }

            when (result) {
                is PushResult.Success -> {
                    syncJobDao.deleteJob(job.jobId)
                }
                is PushResult.SuccessWithMapping -> {
                    // Update cloudEventId if provider returned one
                    eventDao.updateCloudEventId(event.id, result.cloudEventId)
                    syncJobDao.deleteJob(job.jobId)
                }
                is PushResult.Error -> {
                    return if (result.isRetryable) {
                        Result.retry()
                    } else {
                        // Fatal error for this job, discard it
                        syncJobDao.deleteJob(job.jobId)
                        Result.failure()
                    }
                }
            }
        }

        return Result.success()
    }

    private fun parsePayload(eventId: String, payloadJson: String): List<AttendanceRecord> {
        val regex = Regex("\"id\":\"(.*?)\",\"state\":\"(.*?)\",\"time\":(\\d+)")
        return regex.findAll(payloadJson).map { match ->
            AttendanceRecord(
                eventId = eventId,
                attendeeId = match.groupValues[1],
                state = match.groupValues[2],
                timestamp = match.groupValues[3].toLong()
            )
        }.toList()
    }
}
