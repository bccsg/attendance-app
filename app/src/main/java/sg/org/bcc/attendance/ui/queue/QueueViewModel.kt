package sg.org.bcc.attendance.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.data.repository.QueueItem
import sg.org.bcc.attendance.util.EventSuggester
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: AttendanceRepository
) : ViewModel() {

    private val _currentEventTitle = MutableStateFlow(EventSuggester.suggestNextEventTitle())
    
    fun setEventTitle(title: String) {
        _currentEventTitle.value = title
    }

    val queueItems: StateFlow<List<QueueItem>> = repository.getQueueItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isDemoMode: StateFlow<Boolean> = repository.getAllAttendees()
        .map { attendees -> attendees.any { it.id.startsWith("D") } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val presentIds: StateFlow<Set<String>> = _currentEventTitle.flatMapLatest { title ->
        repository.getAttendanceRecords(title).map { records ->
            records.filter { it.state == "PRESENT" }.map { it.attendeeId }.toSet()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    fun removeFromQueue(attendeeId: String) {
        viewModelScope.launch {
            repository.removeFromQueue(attendeeId)
        }
    }

    fun toggleExclusion(attendeeId: String, currentExclusion: Boolean) {
        viewModelScope.launch {
            repository.toggleExclusion(attendeeId, !currentExclusion)
        }
    }

    fun syncQueue(eventTitle: String, state: String) {
        viewModelScope.launch {
            repository.syncQueue(eventTitle, state)
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            repository.clearQueue()
        }
    }

    fun clearActiveQueue() {
        viewModelScope.launch {
            repository.clearActiveQueue()
        }
    }
}
