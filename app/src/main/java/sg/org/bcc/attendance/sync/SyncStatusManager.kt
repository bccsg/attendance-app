package sg.org.bcc.attendance.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.data.remote.AuthState
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncStatusManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: AttendanceRepository,
    private val authManager: AuthManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val activeNetworks = mutableSetOf<Network>()
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _syncProgress = MutableStateFlow(SyncProgress(
        pendingJobs = 0,
        nextScheduledPull = null,
        lastPullTime = null,
        lastPullStatus = "Never",
        lastErrors = emptyList()
    ))
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    // Flag for critical events missing (blocking sync)
    // In a larger app, this might be calculated in repository
    private val _isBlockingEventMissing = MutableStateFlow(false)

    init {
        synchronized(activeNetworks) {
            activeNetworks.addAll(
                connectivityManager.allNetworks.filter { network ->
                    connectivityManager.getNetworkCapabilities(network)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                }
            )
        }
        _isOnline.value = activeNetworks.isNotEmpty()

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(activeNetworks) {
                    activeNetworks.add(network)
                }
                updateOnlineStatus()
            }
            override fun onLost(network: Network) {
                synchronized(activeNetworks) {
                    activeNetworks.remove(network)
                }
                updateOnlineStatus()
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                synchronized(activeNetworks) {
                    if (hasInternet) {
                        activeNetworks.add(network)
                    } else {
                        activeNetworks.remove(network)
                    }
                }
                updateOnlineStatus()
            }
        })

        observeSyncStatus()
    }

    private fun updateOnlineStatus() {
        val isOnline = synchronized(activeNetworks) {
            activeNetworks.isNotEmpty()
        }
        _isOnline.value = isOnline
    }

    private fun isCurrentlyOnline(): Boolean {
        // Robust check: Is there ANY network that has internet capability?
        return connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
    }

    fun setBlockingEventMissing(missing: Boolean) {
        _isBlockingEventMissing.value = missing
    }

    @Suppress("UNCHECKED_CAST")
    private fun observeSyncStatus() {
        combine(
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(SyncScheduler.SYNC_WORK_NAME)
                .onStart { emit(emptyList()) },
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(SyncScheduler.PULL_WORK_NAME)
                .onStart { emit(emptyList()) },
            _isOnline,
            repository.getPendingSyncCount().onStart { emit(0) },
            authManager.authState.onStart { emit(authManager.authState.value) },
            authManager.isAuthed.onStart { emit(authManager.isAuthed.value) },
            authManager.isDemoMode.onStart { emit(authManager.isDemoMode.value) },
            _isBlockingEventMissing,
            repository.isSyncing
        ) { flows ->
            val syncWorkInfos = flows[0] as List<androidx.work.WorkInfo>
            val pullWorkInfos = flows[1] as List<androidx.work.WorkInfo>
            val online = flows[2] as Boolean
            val pendingCount = flows[3] as Int
            val authState = flows[4] as AuthState
            val isAuthed = flows[5] as Boolean
            val isDemoMode = flows[6] as Boolean
            val blockingMissing = flows[7] as Boolean
            val isCloudActive = flows[8] as Boolean

            val syncWorkInfo = syncWorkInfos.firstOrNull()
            val pullWorkInfo = pullWorkInfos.firstOrNull()
            
            val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            val lastPullTimeStored = syncPrefs.getLong("last_pull_time", 0L).let { if (it == 0L) null else it }
            val lastPullStatusStored = syncPrefs.getString("last_pull_status", "Never")
            
            val nextScheduledPull = if (pullWorkInfo?.state == androidx.work.WorkInfo.State.ENQUEUED) {
                pullWorkInfo.nextScheduleTimeMillis.let { if (it == 0L) null else it }
            } else {
                null
            }

            var currentOp: String? = null
            var newState = if (isCloudActive) SyncState.SYNCING else SyncState.IDLE
            var errorMsg: String? = null

            if (syncWorkInfo != null) {
                val progress = syncWorkInfo.progress
                val op = progress.getString(SyncWorker.PROGRESS_OP)
                if (op != null) currentOp = op
                
                val stateStr = progress.getString(SyncWorker.PROGRESS_STATE)
                errorMsg = progress.getString(SyncWorker.PROGRESS_ERROR) ?: syncWorkInfo.outputData.getString(SyncWorker.PROGRESS_ERROR)
                
                if (newState == SyncState.IDLE) {
                    newState = when (stateStr) {
                        "SYNCING" -> SyncState.SYNCING
                        "RETRYING" -> SyncState.RETRYING
                        "IDLE" -> SyncState.IDLE
                        else -> {
                            when (syncWorkInfo.state) {
                                androidx.work.WorkInfo.State.RUNNING -> SyncState.SYNCING
                                androidx.work.WorkInfo.State.ENQUEUED -> SyncState.IDLE
                                androidx.work.WorkInfo.State.FAILED -> SyncState.ERROR
                                else -> SyncState.IDLE
                            }
                        }
                    }
                }
            }

            if (newState == SyncState.IDLE && pullWorkInfo != null) {
                val progress = pullWorkInfo.progress
                val stateStr = progress.getString(PullWorker.PROGRESS_STATE)
                
                if (stateStr == "SYNCING") {
                    val op = progress.getString(PullWorker.PROGRESS_OP)
                    if (op != null) currentOp = op
                    newState = SyncState.SYNCING
                } else if (stateStr == "ERROR") {
                    errorMsg = progress.getString(PullWorker.PROGRESS_ERROR)
                    newState = SyncState.ERROR
                }
            }

            if (isCloudActive && currentOp == null) {
                currentOp = "Synchronizing..."
            }

            if ((newState == SyncState.IDLE || newState == SyncState.RETRYING) && pendingCount > 0 && !online) {
                newState = SyncState.NO_INTERNET
            }

            val currentErrors = _syncProgress.value.lastErrors.toMutableList()
            if (errorMsg != null && (currentErrors.isEmpty() || currentErrors.first().message != errorMsg)) {
                currentErrors.add(0, SyncError(System.currentTimeMillis(), errorMsg))
            } else if (newState == SyncState.SYNCING && errorMsg == null) {
                currentErrors.clear()
            }

            _syncProgress.value = _syncProgress.value.copy(
                pendingJobs = pendingCount,
                currentOperation = currentOp,
                syncState = newState,
                lastErrors = currentErrors,
                nextScheduledPull = nextScheduledPull,
                lastPullTime = lastPullTimeStored,
                lastPullStatus = lastPullStatusStored,
                authState = authState,
                isAuthed = isAuthed,
                isDemoMode = isDemoMode,
                isOnline = online,
                isBlockingEventMissing = blockingMissing
            )
        }.launchIn(scope)
    }
}

// Extension to bridge WorkManager LiveData to Flow
private fun <T> androidx.lifecycle.LiveData<T>.asFlow(): Flow<T> = callbackFlow {
    val observer = androidx.lifecycle.Observer<T> { value ->
        trySend(value)
    }
    observeForever(observer)
    awaitClose {
        removeObserver(observer)
    }
}

// WorkManager doesn't have a direct getWorkInfosForUniqueWorkFlow, so we use the LiveData bridge
private fun WorkManager.getWorkInfosForUniqueWorkFlow(name: String): Flow<List<androidx.work.WorkInfo>> =
    getWorkInfosForUniqueWorkLiveData(name).asFlow()
