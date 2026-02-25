package sg.org.bcc.attendance.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import javax.inject.Inject

@HiltViewModel
class CloudResolutionViewModel @Inject constructor(
    private val repository: AttendanceRepository
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _resolutionError = MutableStateFlow<String?>(null)
    val resolutionError: StateFlow<String?> = _resolutionError.asStateFlow()

    val missingAttendees: StateFlow<List<Attendee>> = repository.getMissingOnCloudAttendees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missingGroups: StateFlow<List<Group>> = repository.getMissingOnCloudGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missingEvents: StateFlow<List<Event>> = repository.getMissingOnCloudEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearError() {
        _resolutionError.value = null
    }

    fun removeAttendee(id: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            _resolutionError.value = null
            try {
                repository.removeAttendeeById(id)
                onSuccess()
            } catch (e: Exception) {
                _resolutionError.value = e.message ?: "Failed to remove attendee"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun removeGroup(id: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            _resolutionError.value = null
            try {
                repository.removeGroupById(id)
                onSuccess()
            } catch (e: Exception) {
                _resolutionError.value = e.message ?: "Failed to remove group"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun recreateEvent(id: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            _resolutionError.value = null
            try {
                repository.resolveEventRecreate(id)
                onSuccess()
            } catch (e: Exception) {
                _resolutionError.value = e.message ?: "Failed to recreate event"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deleteEventLocally(id: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            _resolutionError.value = null
            try {
                repository.resolveEventDeleteLocally(id)
                onSuccess()
            } catch (e: Exception) {
                _resolutionError.value = e.message ?: "Failed to delete event locally"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun purgeAll() {
        viewModelScope.launch {
            _isProcessing.value = true
            _resolutionError.value = null
            try {
                repository.purgeAllMissingFromCloud()
            } catch (e: Exception) {
                _resolutionError.value = e.message ?: "Failed to purge all missing items"
            } finally {
                _isProcessing.value = false
            }
        }
    }
}
