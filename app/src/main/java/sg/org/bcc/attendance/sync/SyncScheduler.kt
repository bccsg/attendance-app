package sg.org.bcc.attendance.sync

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val foregroundStateProvider: ForegroundStateProvider
) {
    fun scheduleSync() {
        // Only schedule if the app is in the foreground
        if (!foregroundStateProvider.isForeground()) {
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    fun schedulePeriodicPull() {
        // Only schedule if the app is in the foreground
        if (!foregroundStateProvider.isForeground()) {
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val randomDelaySeconds = (45..60).random()
        val pullRequest = OneTimeWorkRequestBuilder<PullWorker>()
            .setInitialDelay(randomDelaySeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            PULL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            pullRequest
        )
    }

    fun cancelAllWork() {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(PULL_WORK_NAME)
    }

    companion object {
        const val SYNC_WORK_NAME = "sequential_sync_work"
        const val PULL_WORK_NAME = "scheduled_pull_work"
    }
}
