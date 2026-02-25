package sg.org.bcc.attendance.ui.main

import sg.org.bcc.attendance.data.remote.AuthState
import sg.org.bcc.attendance.ui.components.AppIcons

enum class SyncState {
    IDLE,
    SYNCING,
    RETRYING,
    ERROR,
    NO_INTERNET
}

data class SyncError(
    val timestamp: Long,
    val message: String
)

data class SyncProgress(
    val pendingJobs: Int,
    val currentOperation: String? = null,
    val syncState: SyncState = SyncState.IDLE,
    val nextScheduledPull: Long?,
    val lastPullTime: Long?,
    val lastPullStatus: String?,
    val lastErrors: List<SyncError>,
    val authState: AuthState = AuthState.UNAUTHENTICATED,
    val isAuthed: Boolean = false,
    val isDemoMode: Boolean = true,
    val isOnline: Boolean = false,
    val isBlockingEventMissing: Boolean = false
) {
    val cloudStatusIcon: Int
        get() = when {
            syncState == SyncState.SYNCING -> AppIcons.Sync
            !isOnline || isBlockingEventMissing || syncState == SyncState.ERROR || authState == AuthState.EXPIRED -> AppIcons.CloudAlert
            isDemoMode || !isAuthed -> AppIcons.CloudOff
            else -> AppIcons.CloudDone
        }

    val shouldRotate: Boolean
        get() = syncState == SyncState.SYNCING
}
