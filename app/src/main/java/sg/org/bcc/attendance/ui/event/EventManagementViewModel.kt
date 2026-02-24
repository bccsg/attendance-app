package sg.org.bcc.attendance.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class EventManagementViewModel @Inject constructor(
    private val repository: AttendanceRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _events = repository.getAllEvents()
    
    val isDemoMode = flow {
        emit(repository.isDemoMode())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val manageableEvents: StateFlow<List<Event>> = repository.getManageableEvents()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isSyncing = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            if (!repository.isDemoMode()) {
                isSyncing.value = true
                try {
                    repository.syncRecentEvents(triggerType = "EVENT_REFRESH")
                } finally {
                    isSyncing.value = false
                }
            }
        }
    }

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError: StateFlow<String?> = _uiError.asStateFlow()

    fun clearError() {
        _uiError.value = null
    }

    fun logout(onLogoutComplete: () -> Unit) {
        viewModelScope.launch {
            authManager.logout()
            repository.clearAllData()
            repository.syncMasterList()
            onLogoutComplete()
        }
    }

    fun onCreateEvent(name: String, date: LocalDate, time: String, onCreated: (Event) -> Unit = {}) {
        viewModelScope.launch {
            val datePart = date.format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"))
            val title = "$datePart $time $name"
            
            val existing = repository.findEventByTitleIgnoreCase(title)
            if (existing != null) {
                _uiError.value = "An event with this date, time and name already exists."
                return@launch
            }

            val newEvent = Event(
                title = title,
                date = date.toString(),
                time = time
            )
            repository.insertEvent(newEvent)
            onCreated(newEvent)
        }
    }
}
