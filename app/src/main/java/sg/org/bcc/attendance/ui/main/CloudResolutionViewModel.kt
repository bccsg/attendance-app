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

    val missingAttendees: StateFlow<List<Attendee>> = repository.getMissingOnCloudAttendees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missingGroups: StateFlow<List<Group>> = repository.getMissingOnCloudGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missingEvents: StateFlow<List<Event>> = repository.getMissingOnCloudEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeAttendee(id: String) {
        viewModelScope.launch {
            repository.removeAttendeeById(id)
        }
    }

    fun removeGroup(id: String) {
        viewModelScope.launch {
            repository.removeGroupById(id)
        }
    }

    fun recreateEvent(id: String) {
        viewModelScope.launch {
            repository.resolveEventRecreate(id)
        }
    }

    fun deleteEventLocally(id: String) {
        viewModelScope.launch {
            repository.resolveEventDeleteLocally(id)
        }
    }

    fun purgeAll() {
        viewModelScope.launch {
            repository.purgeAllMissingFromCloud()
        }
    }
}
