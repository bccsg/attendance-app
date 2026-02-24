package sg.org.bcc.attendance.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.util.FuzzySearchScorer
import sg.org.bcc.attendance.util.EventSuggester
import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import sg.org.bcc.attendance.sync.SyncWorker
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.time.LocalDate
import javax.inject.Inject

data class CloudProfile(
    val email: String,
    val profileImageUrl: String? = null
)

data class SyncError(
    val timestamp: Long,
    val message: String
)

enum class SyncState {
    IDLE,
    SYNCING,
    RETRYING,
    ERROR,
    NO_INTERNET
}

data class SyncProgress(
    val pendingJobs: Int,
    val currentOperation: String? = null,
    val syncState: SyncState = SyncState.IDLE,
    val nextScheduledPull: Long?,
    val lastPullTime: Long?,
    val lastPullStatus: String?,
    val lastErrors: List<SyncError>
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainListViewModel @Inject constructor(
    private val repository: AttendanceRepository,
    private val authManager: AuthManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(isCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private fun isCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showPresent = MutableStateFlow(true)
    val showPresent: StateFlow<Boolean> = _showPresent.asStateFlow()

    private val _showAbsent = MutableStateFlow(true)
    val showAbsent: StateFlow<Boolean> = _showAbsent.asStateFlow()

    private val _selectedIds = MutableStateFlow(setOf<String>())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isShowSelectedOnlyMode = MutableStateFlow(false)
    val isShowSelectedOnlyMode: StateFlow<Boolean> = _isShowSelectedOnlyMode.asStateFlow()

    private val _textScale = MutableStateFlow(1.0f)
    val textScale: StateFlow<Float> = _textScale.asStateFlow()

    fun setTextScale(scale: Float) {
        _textScale.value = scale
    }

    val isAuthed = authManager.isAuthed

    val isDemoMode = isAuthed.map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val hasSyncError = MutableStateFlow(false)
    val isSyncing = MutableStateFlow(false)

    val cloudProfile: StateFlow<CloudProfile?> = isAuthed.map { authed ->
        if (authed) {
            CloudProfile(
                email = authManager.getEmail() ?: ""
            )
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _syncProgress = MutableStateFlow(SyncProgress(
        pendingJobs = 0,
        nextScheduledPull = System.currentTimeMillis() + 15 * 60 * 1000,
        lastPullTime = null,
        lastPullStatus = "Never",
        lastErrors = emptyList()
    ))
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private val _showCloudStatusDialog = MutableStateFlow(false)
    val showCloudStatusDialog: StateFlow<Boolean> = _showCloudStatusDialog.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    val requiredDomain = AuthManager.REQUIRED_DOMAIN

    private val _showQueueSheet = MutableStateFlow(false)
    val showQueueSheet: StateFlow<Boolean> = _showQueueSheet.asStateFlow()

    // Events for MainActivity to observe
    private val _loginRequestEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginRequestEvent = _loginRequestEvent.asSharedFlow()

    fun setShowQueueSheet(show: Boolean) {
        _showQueueSheet.value = show
    }

    val syncPendingCount = repository.getPendingSyncCount()
        .onEach { count ->
            _syncProgress.value = _syncProgress.value.copy(pendingJobs = count)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val syncPending = syncPendingCount.map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val availableEvents: StateFlow<List<Event>> = repository.getManageableEvents()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentEventId = MutableStateFlow<String?>(null)
    val currentEventId: StateFlow<String?> = _currentEventId.asStateFlow()

    val currentEvent = _currentEventId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else repository.getAllEvents().map { events -> events.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentEventTitle = currentEvent.map { it?.title ?: "No Event Selected" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "No Event Selected")

    val presentIds = _currentEventId.flatMapLatest { id ->
        if (id == null) flowOf(emptySet<String>())
        else repository.getAttendanceRecords(id).map { records -> 
            records.filter { it.state == "PRESENT" }.map { it.attendeeId }.toSet() 
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val pendingIds = _currentEventId.flatMapLatest { id ->
        if (id == null) flowOf(emptySet<String>())
        else repository.getAttendanceRecords(id).map { records -> 
            records.filter { it.state == "ABSENT" }.map { it.attendeeId }.toSet() 
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val queueIds = repository.getQueueItems()
        .map { items -> items.map { it.attendee.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val attendeeGroupsMap: StateFlow<Map<String, List<String>>> = combine(
        repository.getAllGroups(),
        repository.getAllMappings()
    ) { groups, mappings ->
        val groupNameMap = groups.associate { it.groupId to it.name }
        mappings.groupBy { it.attendeeId }
            .mapValues { (_, attendeeMappings) ->
                attendeeMappings.mapNotNull { groupNameMap[it.groupId] }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _selectedAttendeeForDetail = MutableStateFlow<Attendee?>(null)
    val selectedAttendeeForDetail: StateFlow<Attendee?> = _selectedAttendeeForDetail.asStateFlow()

    sealed class FabState {
        data object QrScanner : FabState()
        data class AddAttendee(val name: String) : FabState()
        data object AddSelection : FabState()
        data object Hidden : FabState()
    }

    val fabState: StateFlow<FabState> = combine(
        _selectedIds,
        _selectedAttendeeForDetail,
        _showQueueSheet,
        queueIds
    ) { selectedIds, detailAttendee, isQueueVisible, qIds ->
        when {
            isQueueVisible -> FabState.QrScanner
            detailAttendee != null -> {
                if (qIds.contains(detailAttendee.id)) FabState.QrScanner
                else FabState.AddAttendee(detailAttendee.shortName ?: detailAttendee.fullName)
            }
            selectedIds.isNotEmpty() -> FabState.AddSelection
            else -> FabState.QrScanner
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FabState.QrScanner)

    private val detailNavigationStack = mutableListOf<Attendee>()
    private val _canNavigateBackInDetail = MutableStateFlow(false)
    val canNavigateBackInDetail: StateFlow<Boolean> = _canNavigateBackInDetail.asStateFlow()

    private val _previousAttendeeName = MutableStateFlow<String?>(null)
    val previousAttendeeName: StateFlow<String?> = _previousAttendeeName.asStateFlow()

    val detailAttendeeGroups: StateFlow<List<sg.org.bcc.attendance.data.local.entities.Group>> = _selectedAttendeeForDetail.flatMapLatest { attendee ->
        if (attendee == null) flowOf(emptyList())
        else combine(repository.getAllGroups(), repository.getAllMappings()) { groups, mappings ->
            val attendeeGroupIds = mappings.filter { it.attendeeId == attendee.id }.map { it.groupId }.toSet()
            groups.filter { it.groupId in attendeeGroupIds }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupMembersMap: StateFlow<Map<String, List<Attendee>>> = detailAttendeeGroups.flatMapLatest { groups ->
        if (groups.isEmpty()) flowOf(emptyMap())
        else {
            val flows = groups.associate { group ->
                group.groupId to repository.getAttendeesByGroup(group.groupId)
            }
            combine(flows.values) { attendeeLists ->
                groups.mapIndexed { index, group ->
                    group.groupId to attendeeLists[index]
                }.toMap()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val allAttendees = repository.getAllAttendees()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val searchFilteredAttendees = combine(
        allAttendees,
        _searchQuery
    ) { attendees, query ->
        if (query.isBlank()) {
            attendees.sortedBy { it.shortName ?: it.fullName }
        } else {
            attendees.filter { 
                (it.shortName?.contains(query, ignoreCase = true) == true) ||
                it.fullName.contains(query, ignoreCase = true)
            }.sortedByDescending { 
                FuzzySearchScorer.score(it, query) 
            }
        }
    }

    // Combine UI state to bypass combine() parameter limit
    private val uiFilterState = combine(
        _showPresent,
        _showAbsent,
        _isShowSelectedOnlyMode
    ) { present, absent, checklist ->
        Triple(present, absent, checklist)
    }

    val attendees: StateFlow<List<Attendee>> = combine(
        searchFilteredAttendees,
        uiFilterState,
        _selectedIds,
        presentIds
    ) { filtered, filters, selectedIds, presentIds ->
        val (showPresent, showAbsent, isShowSelectedOnly) = filters
        filtered.filter { attendee ->
            val isPresent = presentIds.contains(attendee.id)
            val isSelected = selectedIds.contains(attendee.id)
            
            val matchesCategory = (showPresent && isPresent) || (showAbsent && !isPresent)
            val matchesChecklist = !isShowSelectedOnly || isSelected
            
            matchesCategory && matchesChecklist
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queueCount = repository.getQueueItems()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val presentPoolCount: StateFlow<Int> = combine(
        allAttendees,
        presentIds
    ) { all, presentIds ->
        all.count { it.id in presentIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingPoolCount: StateFlow<Int> = combine(
        allAttendees,
        presentIds
    ) { all, presentIds ->
        all.count { it.id !in presentIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val presentBadgeCount: StateFlow<Int> = combine(
        allAttendees,
        presentIds,
        _selectedIds
    ) { all, presentIds, selectedIds ->
        if (selectedIds.isNotEmpty()) {
            selectedIds.count { it in presentIds }
        } else {
            all.count { it.id in presentIds }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingBadgeCount: StateFlow<Int> = combine(
        allAttendees,
        presentIds,
        _selectedIds
    ) { all, presentIds, selectedIds ->
        if (selectedIds.isNotEmpty()) {
            selectedIds.count { it !in presentIds }
        } else {
            all.count { it.id !in presentIds }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Statistics for Cloud Status Dialog
    val totalAttendeesCount = allAttendees.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalGroupsCount = repository.getAllGroups().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    val attendeesWithGroupCount = attendeeGroupsMap.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _isOnline.value = true }
            override fun onLost(network: Network) { _isOnline.value = false }
        })

        // Observe WorkManager progress combined with connectivity and pending count
        combine(
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData("sequential_sync_work")
                .asFlow(),
            _isOnline,
            repository.getPendingSyncCount()
        ) { workInfos, online, pendingCount ->
            val workInfo = workInfos?.firstOrNull()
            
            var currentOp: String? = null
            var newState = SyncState.IDLE
            var errorMsg: String? = null

            if (workInfo != null) {
                val progress = workInfo.progress
                currentOp = progress.getString(SyncWorker.PROGRESS_OP)
                val stateStr = progress.getString(SyncWorker.PROGRESS_STATE)
                errorMsg = progress.getString(SyncWorker.PROGRESS_ERROR) ?: workInfo.outputData.getString(SyncWorker.PROGRESS_ERROR)
                
                newState = when (stateStr) {
                    "SYNCING" -> SyncState.SYNCING
                    "RETRYING" -> SyncState.RETRYING
                    "IDLE" -> SyncState.IDLE
                    else -> {
                        when (workInfo.state) {
                            androidx.work.WorkInfo.State.RUNNING -> SyncState.SYNCING
                            androidx.work.WorkInfo.State.ENQUEUED -> SyncState.IDLE
                            androidx.work.WorkInfo.State.FAILED -> SyncState.ERROR
                            else -> SyncState.IDLE
                        }
                    }
                }
            }

            // Override status if pending jobs are blocked by lack of internet
            if ((newState == SyncState.IDLE || newState == SyncState.RETRYING) && pendingCount > 0 && !online) {
                newState = SyncState.NO_INTERNET
            }

            val currentErrors = _syncProgress.value.lastErrors.toMutableList()
            if (errorMsg != null && (currentErrors.isEmpty() || currentErrors.first().message != errorMsg)) {
                currentErrors.add(0, SyncError(System.currentTimeMillis(), errorMsg))
            } else if (newState == SyncState.SYNCING && errorMsg == null) {
                // Clear errors on fresh start of a sync operation
                currentErrors.clear()
            }

            _syncProgress.value = _syncProgress.value.copy(
                pendingJobs = pendingCount,
                currentOperation = currentOp,
                syncState = newState,
                lastErrors = currentErrors
            )
            
            isSyncing.value = newState == SyncState.SYNCING
            hasSyncError.value = newState == SyncState.ERROR
        }.launchIn(viewModelScope)

        // Trigger any pending sync jobs on app start (robustness mop-up)
        viewModelScope.launch {
            repository.retrySync()
        }

        // Initial seed of demo data if the database is empty on first launch
        viewModelScope.launch {
            if (repository.getAllAttendees().first().isEmpty()) {
                isSyncing.value = true
                try {
                    repository.syncMasterList()
                } finally {
                    isSyncing.value = false
                }
            }
        }

        viewModelScope.launch {
            // Purge old events on start
            repository.purgeOldEvents()
            
            // Auto-switch logic
            availableEvents.collect { events ->
                if (events.isEmpty()) {
                    _currentEventId.value = null
                    return@collect
                }

                val current = currentEvent.first()
                val cutoff = LocalDate.now().minusDays(30)
                val isExpired = current?.let { 
                    val date = EventSuggester.parseDate(it.title)
                    date == null || date.isBefore(cutoff)
                } ?: true

                if (isExpired || _currentEventId.value == null) {
                    _currentEventId.value = events.firstOrNull()?.id
                }
            }
        }

        combine(presentPoolCount, _showPresent, _showAbsent) { count, present, absent ->
            if (count == 0 && !absent) {
                _showAbsent.value = true
            }
        }.launchIn(viewModelScope)

        combine(pendingPoolCount, _showPresent, _showAbsent) { count, present, absent ->
            if (count == 0 && !present) {
                _showPresent.value = true
            }
        }.launchIn(viewModelScope)

        // Reset checklist mode if selection becomes empty
        _selectedIds.map { it.isEmpty() }
            .distinctUntilChanged()
            .onEach { if (it) _isShowSelectedOnlyMode.value = false }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onShowPresentToggle() {
        _isShowSelectedOnlyMode.value = false
        if (_showPresent.value && _showAbsent.value) {
            _showPresent.value = true
            _showAbsent.value = false
        } else if (!_showPresent.value) {
            _showPresent.value = true
        }
    }

    fun onShowAbsentToggle() {
        _isShowSelectedOnlyMode.value = false
        if (_showPresent.value && _showAbsent.value) {
            _showPresent.value = false
            _showAbsent.value = true
        } else if (!_showAbsent.value) {
            _showAbsent.value = true
        }
    }

    fun toggleShowSelectedOnlyMode() {
        _isShowSelectedOnlyMode.value = !_isShowSelectedOnlyMode.value
        if (_isShowSelectedOnlyMode.value) {
            _showPresent.value = true
            _showAbsent.value = true
        }
    }

    fun deactivateShowSelectedOnlyMode() {
        _isShowSelectedOnlyMode.value = false
    }

    fun enterSelectionMode(attendeeId: String) {
        viewModelScope.launch {
            val currentQueueIds = repository.getQueueItems().first().map { it.attendee.id }.toSet()
            _selectedIds.value = currentQueueIds + attendeeId
        }
    }

    fun toggleSelection(attendeeId: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(attendeeId)) {
            current.remove(attendeeId)
        } else {
            current.add(attendeeId)
        }
        _selectedIds.value = current
    }

    fun showAttendeeDetail(attendee: Attendee) {
        _selectedAttendeeForDetail.value?.let { current ->
            if (current.id != attendee.id) {
                detailNavigationStack.add(current)
                _canNavigateBackInDetail.value = true
                _previousAttendeeName.value = current.shortName ?: current.fullName
            }
        }
        _selectedAttendeeForDetail.value = attendee
    }

    fun popAttendeeDetail() {
        if (detailNavigationStack.isNotEmpty()) {
            val previous = detailNavigationStack.removeAt(detailNavigationStack.size - 1)
            _selectedAttendeeForDetail.value = previous
            _canNavigateBackInDetail.value = detailNavigationStack.isNotEmpty()
            _previousAttendeeName.value = detailNavigationStack.lastOrNull()?.let { it.shortName ?: it.fullName }
        }
    }

    fun dismissAttendeeDetail() {
        _selectedAttendeeForDetail.value = null
        detailNavigationStack.clear()
        _canNavigateBackInDetail.value = false
        _previousAttendeeName.value = null
    }

    fun addAttendeeToQueue(attendeeId: String) {
        viewModelScope.launch {
            val currentQueueIds = repository.getQueueItems().first().map { it.attendee.id }.toSet()
            repository.replaceQueueWithSelection((currentQueueIds + attendeeId).toList())
        }
    }

    fun addGroupToQueue(groupId: String) {
        viewModelScope.launch {
            val members = repository.getAttendeesByGroup(groupId).first()
            val memberIds = members.map { it.id }
            val currentQueueIds = repository.getQueueItems().first().map { it.attendee.id }.toSet()
            val combinedIds = (currentQueueIds + memberIds).toList()
            repository.replaceQueueWithSelection(combinedIds)
            dismissAttendeeDetail()
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isShowSelectedOnlyMode.value = false
    }

    fun confirmSelection() {
        viewModelScope.launch {
            repository.replaceQueueWithSelection(_selectedIds.value.toList())
            _selectedIds.value = emptySet()
            _isShowSelectedOnlyMode.value = false
        }
    }

    fun onSyncMasterList() {
        setShowCloudStatusDialog(true)
    }

    fun doManualSync() {
        viewModelScope.launch {
            isSyncing.value = true
            try {
                val (syncSuccess, detailedStatus) = repository.syncMasterListWithDetailedResult()
                if (!syncSuccess) {
                    _loginError.value = detailedStatus
                } else {
                    _loginError.value = null // Clear any previous errors on success
                }
            } catch (e: Exception) {
                _loginError.value = "Sync failed: ${e.message}"
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun setShowCloudStatusDialog(show: Boolean) {
        _showCloudStatusDialog.value = show
        if (!show) {
            _loginError.value = null
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun onLoginError(message: String) {
        _loginError.value = message
    }

    fun onLoginTrigger() {
        _loginError.value = null
        viewModelScope.launch {
            _loginRequestEvent.emit(Unit)
        }
    }

    fun getAuthUrl(): String = authManager.getAuthUrl()

    fun handleOAuthCode(code: String) {
        android.util.Log.d("AttendanceAuth", "ViewModel handling OAuth code: ${code.take(5)}...")
        viewModelScope.launch {
            isSyncing.value = true
            try {
                // 1. Exchange code for tokens
                android.util.Log.d("AttendanceAuth", "Exchanging code for tokens...")
                val exchangeSuccess = authManager.exchangeCodeForTokens(code)
                if (!exchangeSuccess) {
                    android.util.Log.e("AttendanceAuth", "Token exchange FAILED!")
                    _loginError.value = "Failed to exchange code for tokens. Ensure you use a @${AuthManager.REQUIRED_DOMAIN} account."
                    return@launch
                }

                android.util.Log.d("AttendanceAuth", "Exchange success! Clearing old data and attempting master sync...")
                // 2. Clear all local data before syncing with the cloud
                repository.clearAllData()
                
                // 3. Attempt sync with new tokens
                val (syncSuccess, detailedStatus) = repository.syncMasterListWithDetailedResult()
                android.util.Log.d("AttendanceAuth", "Sync success: $syncSuccess, status:\n$detailedStatus")
                
                if (syncSuccess) {
                    _loginError.value = null
                } else {
                    // For now, logout to ensure clean state if sync fails
                    authManager.logout()
                    _loginError.value = detailedStatus
                }
            } catch (e: Exception) {
                android.util.Log.e("AttendanceAuth", "Error during OAuth flow: ${e.message}", e)
                _loginError.value = "Login error: ${e.message}"
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            isSyncing.value = true
            try {
                authManager.logout()
                repository.clearAllData()
                repository.syncMasterList()
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun onSwitchEvent(eventId: String) {
        _currentEventId.value = eventId
        viewModelScope.launch {
            repository.getEventById(eventId)?.let { event ->
                repository.syncAttendanceForEvent(event)
            }
        }
    }

    // setTextScale is already defined above
}
