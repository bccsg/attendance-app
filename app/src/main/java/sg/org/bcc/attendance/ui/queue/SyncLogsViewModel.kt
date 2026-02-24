package sg.org.bcc.attendance.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.local.entities.SyncLog
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import javax.inject.Inject

@HiltViewModel
class SyncLogsViewModel @Inject constructor(
    private val repository: AttendanceRepository
) : ViewModel() {

    val triggersSummary = repository.getSyncLogsSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTriggerId = MutableStateFlow<String?>(null)
    val selectedTriggerId: StateFlow<String?> = _selectedTriggerId.asStateFlow()

    private val _logsForSelectedTrigger = MutableStateFlow<List<SyncLog>>(emptyList())
    val logsForSelectedTrigger: StateFlow<List<SyncLog>> = _logsForSelectedTrigger.asStateFlow()

    fun selectTrigger(triggerId: String?) {
        _selectedTriggerId.value = triggerId
        if (triggerId != null) {
            viewModelScope.launch {
                repository.getLogsForTrigger(triggerId).collect {
                    _logsForSelectedTrigger.value = it
                }
            }
        } else {
            _logsForSelectedTrigger.value = emptyList()
        }
    }
}
