package sg.org.bcc.attendance.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.data.repository.QueueItem
import sg.org.bcc.attendance.data.remote.AuthManager
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: AttendanceRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _currentEventId = MutableStateFlow<String?>(null)
    
    fun setEventId(id: String?) {
        _currentEventId.value = id
    }

    val queueItems: StateFlow<List<QueueItem>> = repository.getQueueItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isDemoMode: StateFlow<Boolean> = authManager.isAuthed
        .map { !it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val presentIds: StateFlow<Set<String>> = _currentEventId.flatMapLatest { id ->
        if (id == null) flowOf(emptySet())
        else repository.getAttendanceRecords(id).map { records ->
            records.filter { it.state == "PRESENT" }.map { it.attendeeId }.toSet()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    val manageableEvents: StateFlow<List<sg.org.bcc.attendance.data.local.entities.Event>> = repository.getManageableEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun removeFromQueue(attendeeId: String) {
        repository.removeFromQueue(attendeeId)
    }

    suspend fun toggleLater(attendeeId: String, currentLater: Boolean) {
        repository.toggleLater(attendeeId, !currentLater)
    }

    suspend fun syncQueue(eventId: String, state: String) {
        repository.syncQueue(eventId, state)
    }

    suspend fun clearQueue() {
        repository.clearQueue()
    }

    suspend fun clearReadyQueue() {
        repository.clearReadyQueue()
    }
}
