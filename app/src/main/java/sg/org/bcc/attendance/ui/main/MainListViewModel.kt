package sg.org.bcc.attendance.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.util.EventSuggester
import sg.org.bcc.attendance.util.FuzzySearchScorer
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainListViewModel @Inject constructor(
    private val repository: AttendanceRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _showPresent = MutableStateFlow(true)
    val showPresent = _showPresent.asStateFlow()

    private val _showAbsent = MutableStateFlow(true)
    val showAbsent = _showAbsent.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    private val _textScale = MutableStateFlow(1.0f)
    val textScale = _textScale.asStateFlow()

    val isDemoMode: StateFlow<Boolean> = repository.getAllAttendees()
        .map { attendees -> attendees.any { it.id.startsWith("D") } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _currentEventTitle = MutableStateFlow(EventSuggester.suggestNextEventTitle())
    val currentEventTitle = _currentEventTitle.asStateFlow()

    // Simplified list of events for the demo menu
    val availableEvents: StateFlow<List<String>> = flowOf(listOf(
        EventSuggester.suggestNextEventTitle(),
        "260215 1030 sunday service",
        "260208 1030 sunday service"
    )).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val searchFilteredAttendees: StateFlow<List<Attendee>> = combine(
        repository.getAllAttendees(),
        _searchQuery
    ) { all, query ->
        if (query.isBlank()) all else FuzzySearchScorer.sort(all, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendees: StateFlow<List<Attendee>> = combine(
        searchFilteredAttendees,
        currentEventTitle.flatMapLatest { repository.getAttendanceRecords(it) },
        _showPresent,
        _showAbsent
    ) { filtered, records, showPresent, showAbsent ->
        val presentIds = records.filter { it.state == "PRESENT" }.map { it.attendeeId }.toSet()
        
        filtered.filter { attendee ->
            val isPresent = attendee.id in presentIds
            if (isPresent) showPresent else showAbsent
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val presentIds: StateFlow<Set<String>> = currentEventTitle.flatMapLatest { repository.getAttendanceRecords(it) }
        .map { records -> records.filter { it.state == "PRESENT" }.map { it.attendeeId }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val pendingIds: StateFlow<Set<String>> = currentEventTitle.flatMapLatest { repository.getAttendanceRecords(it) }
        .map { records -> records.filter { it.state == "ABSENT" }.map { it.attendeeId }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    fun onShowPresentToggle() {
        if (_showPresent.value && _showAbsent.value) {
            // Both active: isolate Present
            _showAbsent.value = false
        } else if (!_showPresent.value) {
            // Inactive: toggle back on
            _showPresent.value = true
        }
        // If already isolated, we ignore the click to prevent empty state (unless required otherwise)
    }

    fun onShowAbsentToggle() {
        if (_showPresent.value && _showAbsent.value) {
            // Both active: isolate Pending
            _showPresent.value = false
        } else if (!_showAbsent.value) {
            // Inactive: toggle back on
            _showAbsent.value = true
        }
    }

    // Counts for chips: Respect search, ignore visibility toggles, reflect selection if active.
    val presentCategoryCount: StateFlow<Int> = combine(
        searchFilteredAttendees,
        presentIds,
        _selectedIds
    ) { filtered, presentIds, selectedIds ->
        val context = if (selectedIds.isNotEmpty()) filtered.filter { it.id in selectedIds } else filtered
        context.count { it.id in presentIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingCategoryCount: StateFlow<Int> = combine(
        searchFilteredAttendees,
        presentIds,
        _selectedIds
    ) { filtered, presentIds, selectedIds ->
        val context = if (selectedIds.isNotEmpty()) filtered.filter { it.id in selectedIds } else filtered
        context.count { it.id !in presentIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        // Safety: ensure at least one category is visible if counts are zero
        combine(presentCategoryCount, _showPresent, _showAbsent) { count, present, absent ->
            if (count == 0 && !absent) {
                _showAbsent.value = true
            }
        }.launchIn(viewModelScope)

        combine(pendingCategoryCount, _showPresent, _showAbsent) { count, present, absent ->
            if (count == 0 && !present) {
                _showPresent.value = true
            }
        }.launchIn(viewModelScope)
    }

    fun onSwitchEvent(title: String) {
        _currentEventTitle.value = title
    }

    fun onCreateEvent(name: String) {
        val currentPrefix = _currentEventTitle.value.split(" ").take(2).joinToString(" ")
        val title = "$currentPrefix $name"
        _currentEventTitle.value = title
    }

    fun enterSelectionMode(firstAttendeeId: String) {
        viewModelScope.launch {
            val currentQueueIds = repository.getQueueItems().first().map { it.attendee.id }.toSet()
            _selectedIds.value = currentQueueIds + firstAttendeeId
        }
    }

    fun toggleSelection(attendeeId: String) {
        _selectedIds.value = if (_selectedIds.value.contains(attendeeId)) {
            _selectedIds.value - attendeeId
        } else {
            _selectedIds.value + attendeeId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun confirmSelection() {
        viewModelScope.launch {
            repository.replaceQueueWithSelection(_selectedIds.value.toList())
            clearSelection()
        }
    }

    val presentCount: StateFlow<Int> = currentEventTitle.flatMapLatest { repository.getAttendanceRecords(it) }
        .map { records -> records.count { it.state == "PRESENT" } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val syncPending: StateFlow<Boolean> = repository.getPendingSyncCount()
        .map { it > 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val queueCount: StateFlow<Int> = repository.getQueueItems()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val queueIds: StateFlow<Set<String>> = repository.getQueueItems()
        .map { items -> items.map { it.attendee.id }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onSyncMasterList() {
        viewModelScope.launch {
            repository.syncMasterList()
        }
    }

    fun toggleTextScale() {
        _textScale.value = if (_textScale.value == 1.0f) 1.5f else 1.0f
    }
}
