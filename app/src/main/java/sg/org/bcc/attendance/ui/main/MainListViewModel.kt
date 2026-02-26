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
import sg.org.bcc.attendance.data.remote.AuthState
import sg.org.bcc.attendance.util.FuzzySearchScorer
import sg.org.bcc.attendance.util.EventSuggester
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import sg.org.bcc.attendance.sync.*
import sg.org.bcc.attendance.util.qr.QrInfo
import java.time.LocalDate
import javax.inject.Inject
import androidx.core.content.edit

data class CloudProfile(
    val email: String,
    val profileImageUrl: String? = null
)

data class QrSelection(
    val attendee: Attendee,
    val groups: List<sg.org.bcc.attendance.data.local.entities.Group>
)

enum class SortMode {
    NAME_ASC,
    RECENT_UPDATED
}

enum class SheetType {
    NONE,
    ATTENDEE_DETAIL,
    QUEUE,
    QR_SCANNER
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainListViewModel @Inject constructor(
    private val repository: AttendanceRepository,
    private val authManager: AuthManager,
    private val syncStatusManager: SyncStatusManager,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    val isOnline = syncStatusManager.isOnline
    val syncProgress = syncStatusManager.syncProgress

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

    private val _sortMode = MutableStateFlow(SortMode.entries.find { it.name == prefs.getString("sort_mode", SortMode.NAME_ASC.name) } ?: SortMode.NAME_ASC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        prefs.edit { putString("sort_mode", mode.name) }
    }

    private val _textScale = MutableStateFlow(1.0f)
    val textScale: StateFlow<Float> = _textScale.asStateFlow()

    fun setTextScale(scale: Float) {
        _textScale.value = scale
    }

    val isAuthed = authManager.isAuthed
    val authState = authManager.authState
    val isDemoMode = authManager.isDemoMode

    val hasSyncError = syncProgress.map { it.syncState == SyncState.ERROR }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isSyncing = repository.isSyncing

    val cloudProfile: StateFlow<CloudProfile?> = isAuthed.map { authed ->
        if (authed) {
            CloudProfile(
                email = authManager.getEmail() ?: ""
            )
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showCloudStatusDialog = MutableStateFlow(false)
    val showCloudStatusDialog: StateFlow<Boolean> = _showCloudStatusDialog.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    val requiredDomain = AuthManager.REQUIRED_DOMAIN

    private val _activeSheet = MutableStateFlow(SheetType.NONE)
    val activeSheet: StateFlow<SheetType> = _activeSheet.asStateFlow()

    private val _showQueueSheet = _activeSheet.map { it == SheetType.QUEUE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showQueueSheet: StateFlow<Boolean> = _showQueueSheet

    private val _showScannerSheet = _activeSheet.map { it == SheetType.QR_SCANNER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showScannerSheet: StateFlow<Boolean> = _showScannerSheet

    // Events for MainActivity to observe
    private val _loginRequestEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginRequestEvent = _loginRequestEvent.asSharedFlow()

    private val _navigateToResolutionScreenEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToResolutionScreenEvent = _navigateToResolutionScreenEvent.asSharedFlow()

    private val _qrSelection = MutableStateFlow<QrSelection?>(null)
    val qrSelection: StateFlow<QrSelection?> = _qrSelection.asStateFlow()

    private val _activeQrInfo = MutableStateFlow<QrInfo?>(null)
    val activeQrInfo: StateFlow<QrInfo?> = _activeQrInfo.asStateFlow()

    fun onQrTrigger(attendee: Attendee, groups: List<sg.org.bcc.attendance.data.local.entities.Group>) {
        if (groups.isEmpty()) {
            onQrSelected(attendee, null)
        } else {
            _qrSelection.value = QrSelection(attendee, groups)
        }
    }

    fun onQrSelected(attendee: Attendee, group: sg.org.bcc.attendance.data.local.entities.Group?) {
        _qrSelection.value = null
        val info = if (group != null) {
            QrInfo(
                personId = attendee.id,
                personName = attendee.shortName ?: attendee.fullName,
                groupId = group.groupId,
                groupName = group.name
            )
        } else {
            QrInfo(personId = attendee.id, personName = attendee.shortName ?: attendee.fullName)
        }
        _activeQrInfo.value = info
    }

    fun dismissQrSelection() {
        _qrSelection.value = null
    }

    private val _qrMessageEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val qrMessageEvent = _qrMessageEvent.asSharedFlow()

    fun processQrResult(code: String): Boolean {
        val info = sg.org.bcc.attendance.util.qr.QrUrlParser.parse(code)
        if (info != null && info.isValid()) {
            viewModelScope.launch {
                val message = repository.processQrInfo(info)
                _qrMessageEvent.emit(message)
                _activeSheet.value = SheetType.QUEUE
            }
            return true
        }
        return false
    }

    fun setShowQueueSheet(show: Boolean) {
        _activeSheet.value = if (show) SheetType.QUEUE else SheetType.NONE
    }

    fun setShowScannerSheet(show: Boolean) {
        _activeSheet.value = if (show) SheetType.QR_SCANNER else SheetType.NONE
    }

    val missingCloudAttendees = repository.getMissingOnCloudAttendees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missingCloudGroups = repository.getMissingOnCloudGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missingCloudAttendeesCount = missingCloudAttendees.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val missingCloudGroupsCount = missingCloudGroups.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val missingCloudEvents = repository.getMissingOnCloudEvents()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val missingCloudEventsCount = missingCloudEvents.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val syncPending = syncProgress.map { it.pendingJobs > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val availableEvents: StateFlow<List<Event>> = repository.getManageableEvents()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentEventId = MutableStateFlow<String?>(prefs.getString("selected_event_id", null))
    val currentEventId: StateFlow<String?> = _currentEventId.asStateFlow()

    val isBlockingEventMissing: StateFlow<Boolean> = syncProgress.map { it.isBlockingEventMissing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onNavigateToResolutionScreen() {
        _navigateToResolutionScreenEvent.tryEmit(Unit)
        _showCloudStatusDialog.value = false
    }

    fun resolveEventRecreate(eventId: String) {
        viewModelScope.launch {
            repository.resolveEventRecreate(eventId)
        }
    }

    fun resolveEventDeleteLocally(eventId: String) {
        viewModelScope.launch {
            if (_currentEventId.value == eventId) {
                _currentEventId.value = null
                prefs.edit { remove("selected_event_id") }
            }
            repository.resolveEventDeleteLocally(eventId)
        }
    }

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

    val allAttendanceRecords = _currentEventId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList<sg.org.bcc.attendance.data.local.entities.AttendanceRecord>())
        else repository.getAttendanceRecords(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            attendees
        } else {
            attendees.filter { 
                (it.shortName?.contains(query, ignoreCase = true) == true) ||
                it.fullName.contains(query, ignoreCase = true)
            }.sortedByDescending { 
                FuzzySearchScorer.score(it, query) 
            }
        }
    }

    private val uiFilterState = combine(
        _showPresent,
        _showAbsent,
        _isShowSelectedOnlyMode,
        _sortMode
    ) { present, absent, checklist, sort ->
        DataFilters(present, absent, checklist, sort)
    }

    data class DataFilters(
        val showPresent: Boolean,
        val showAbsent: Boolean,
        val isShowSelectedOnly: Boolean,
        val sortMode: SortMode
    )

    private val attendeeData = combine(
        searchFilteredAttendees,
        _selectedIds,
        presentIds,
        allAttendanceRecords,
        _searchQuery
    ) { filtered, selectedIds, presentIds, records, query ->
        AttendeeData(filtered, selectedIds, presentIds, records, query)
    }

    data class AttendeeData(
        val filtered: List<Attendee>,
        val selectedIds: Set<String>,
        val presentIds: Set<String>,
        val allAttendanceRecords: List<sg.org.bcc.attendance.data.local.entities.AttendanceRecord>,
        val query: String
    )

    val attendees: StateFlow<List<Attendee>> = combine(
        attendeeData,
        uiFilterState
    ) { data: AttendeeData, filters: DataFilters ->
        val recordMap = data.allAttendanceRecords.associateBy { it.attendeeId }
        
        val result = data.filtered.filter { attendee ->
            val isPresent = data.presentIds.contains(attendee.id)
            val isSelected = data.selectedIds.contains(attendee.id)
            
            val matchesCategory = (filters.showPresent && isPresent) || (filters.showAbsent && !isPresent)
            val matchesChecklist = !filters.isShowSelectedOnly || isSelected
            
            matchesCategory && matchesChecklist
        }

        if (data.query.isNotBlank()) {
            result
        } else {
            when (filters.sortMode) {
                SortMode.NAME_ASC -> {
                    result.sortedBy { (it.shortName ?: it.fullName).lowercase() }
                }
                SortMode.RECENT_UPDATED -> {
                    result.sortedByDescending { recordMap[it.id]?.timestamp ?: 0L }
                }
            }
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

    val totalAttendeesCount = allAttendees.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalGroupsCount = repository.getAllGroups().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    val attendeesWithGroupCount = attendeeGroupsMap.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        combine(
            repository.getOldestPendingEventId(),
            _currentEventId,
            missingCloudEvents
        ) { oldestId, currentId, missingEvents ->
            val oldestMissing = oldestId != null && missingEvents.any { it.id == oldestId }
            val currentMissing = currentId != null && missingEvents.any { it.id == currentId }
            oldestMissing || currentMissing
        }.onEach {
            syncStatusManager.setBlockingEventMissing(it)
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            repository.retrySync()
        }

        viewModelScope.launch {
            if (!repository.isDemoMode()) {
                try {
                    repository.syncMasterList(targetEventId = null)
                } catch (e: Exception) {
                    android.util.Log.e("AttendanceSync", "App start sync failed: ${e.message}")
                }
            }
        }

        viewModelScope.launch {
            repository.purgeOldEvents()
            
            availableEvents.collect { events ->
                if (events.isEmpty()) return@collect

                val currentId = _currentEventId.value
                val currentEventObj = events.find { it.id == currentId }
                
                var isExpired = false
                if (currentEventObj != null) {
                    val date = EventSuggester.parseDate(currentEventObj.title)
                    val cutoff = LocalDate.now().minusDays(30)
                    isExpired = date == null || date.isBefore(cutoff)
                }

                if (currentEventObj == null || isExpired) {
                    val now = java.time.LocalDateTime.now()
                    val oneHourAgo = now.minusHours(1)
                    
                    val suggestedEvent = repository.getUpcomingEvent(oneHourAgo) ?: repository.getLatestEvent()
                    
                    val newId = suggestedEvent?.id
                    _currentEventId.value = newId
                    if (newId != null) {
                        prefs.edit { putString("selected_event_id", newId) }
                    } else {
                        prefs.edit { remove("selected_event_id") }
                    }
                }
            }
        }

        // Trigger attendance sync when event selection changes (covers app start suggestion)
        _currentEventId
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { eventId ->
                if (!repository.isDemoMode()) {
                    repository.getEventById(eventId)?.let { event ->
                        repository.syncAttendanceForEvent(event, triggerType = "EVENT_AUTOSELECT")
                    }
                }
            }
            .launchIn(viewModelScope)

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
                _activeQrInfo.value = null
            }
        }
        _selectedAttendeeForDetail.value = attendee
        _activeSheet.value = SheetType.ATTENDEE_DETAIL
    }

    fun popAttendeeDetail() {
        if (detailNavigationStack.isNotEmpty()) {
            val previous = detailNavigationStack.removeAt(detailNavigationStack.size - 1)
            _selectedAttendeeForDetail.value = previous
            _activeQrInfo.value = null
            _canNavigateBackInDetail.value = detailNavigationStack.isNotEmpty()
            _previousAttendeeName.value = detailNavigationStack.lastOrNull()?.let { it.shortName ?: it.fullName }
        }
    }

    fun dismissAttendeeDetail() {
        _selectedAttendeeForDetail.value = null
        _activeQrInfo.value = null
        detailNavigationStack.clear()
        _canNavigateBackInDetail.value = false
        _previousAttendeeName.value = null
        if (_activeSheet.value == SheetType.ATTENDEE_DETAIL) {
            _activeSheet.value = SheetType.NONE
        }
    }

    fun dismissAllSheets() {
        _selectedAttendeeForDetail.value = null
        _activeQrInfo.value = null
        detailNavigationStack.clear()
        _canNavigateBackInDetail.value = false
        _previousAttendeeName.value = null
        _activeSheet.value = SheetType.NONE
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
            val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            
            try {
                val (syncSuccess, detailedStatus) = repository.syncMasterListWithDetailedResult(targetEventId = _currentEventId.value)
                val now = System.currentTimeMillis()
                if (!syncSuccess) {
                    _loginError.value = detailedStatus
                    syncPrefs.edit().putString("last_pull_status", "Failed").apply()
                } else {
                    _loginError.value = null
                    syncPrefs.edit().putLong("last_pull_time", now).putString("last_pull_status", "Success").apply()
                }
            } catch (e: Exception) {
                val errorMsg = "Sync failed: ${e.message}"
                _loginError.value = errorMsg
                syncPrefs.edit().putString("last_pull_status", "Error").apply()
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
        viewModelScope.launch {
            try {
                val oldEmail = authManager.getEmail()
                val oldIsDemo = authManager.isDemoMode.value

                val exchangeSuccess = authManager.exchangeCodeForTokens(code)
                if (!exchangeSuccess) {
                    _loginError.value = "Failed to exchange code for tokens. Ensure you use a @${AuthManager.REQUIRED_DOMAIN} account."
                    return@launch
                }

                val newEmail = authManager.getEmail()
                val identityChanged = oldEmail != newEmail || oldIsDemo

                if (identityChanged) {
                    repository.clearAllData()
                    _currentEventId.value = null
                    prefs.edit { remove("selected_event_id") }
                } 
                
                val (syncSuccess, detailedStatus) = repository.syncMasterListWithDetailedResult(
                    triggerType = "LOGIN",
                    targetEventId = _currentEventId.value
                )
                
                if (syncSuccess) {
                    _loginError.value = null
                } else {
                    authManager.logout()
                    _loginError.value = detailedStatus
                }
            } catch (e: Exception) {
                _loginError.value = "Login error: ${e.message}"
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            try {
                authManager.logout()
                repository.clearAllData()
                _currentEventId.value = null
                prefs.edit { remove("selected_event_id") }
                repository.syncMasterList(targetEventId = null)
            } finally {
                // No-op
            }
        }
    }

    fun onSwitchEvent(eventId: String) {
        _currentEventId.value = eventId
        prefs.edit { putString("selected_event_id", eventId) }
        
        viewModelScope.launch {
            repository.getEventById(eventId)?.let { event ->
                repository.syncAttendanceForEvent(event, triggerType = "EVENT_SWITCH")
            }
        }
    }
}
