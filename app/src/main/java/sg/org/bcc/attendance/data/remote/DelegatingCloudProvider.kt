package sg.org.bcc.attendance.data.remote

import kotlinx.coroutines.flow.*
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendeeGroupMapping
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.data.remote.fake.DemoCloudProvider
import sg.org.bcc.attendance.sync.SyncLogScope
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class DelegatingCloudProvider @Inject constructor(
    private val authManager: AuthManager,
    private val demoProvider: Provider<DemoCloudProvider>,
    private val gsheetsProvider: Provider<GoogleSheetsAdapter>
) : AttendanceCloudProvider {

    private val providerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private val _activeOperationsCount = MutableStateFlow(0)
    
    override val isSyncing: StateFlow<Boolean> = _activeOperationsCount
        .map { it > 0 }
        .debounce { active -> if (active) 0L else 500L }
        .distinctUntilChanged()
        .stateIn(
            scope = providerScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    override val syncMessages: Flow<String> = authManager.isAuthed.flatMapLatest { authed ->
        if (authed) {
            gsheetsProvider.get().syncMessages
        } else {
            demoProvider.get().syncMessages
        }
    }

    private val activeProvider: AttendanceCloudProvider
        get() = if (authManager.isAuthed.value) {
            gsheetsProvider.get()
        } else {
            demoProvider.get()
        }

    private suspend fun <T> track(block: suspend () -> T): T {
        _activeOperationsCount.update { it + 1 }
        return try {
            block()
        } finally {
            _activeOperationsCount.update { (it - 1).coerceAtLeast(0) }
        }
    }

    override suspend fun pushAttendance(
        event: Event, 
        records: List<AttendanceRecord>,
        scope: SyncLogScope,
        failIfMissing: Boolean
    ): PushResult = track {
        activeProvider.pushAttendance(event, records, scope, failIfMissing)
    }

    override suspend fun fetchMasterAttendees(scope: SyncLogScope): List<Attendee> = track {
        activeProvider.fetchMasterAttendees(scope)
    }

    override suspend fun fetchMasterGroups(scope: SyncLogScope): List<Group> = track {
        activeProvider.fetchMasterGroups(scope)
    }

    override suspend fun fetchAttendeeGroupMappings(scope: SyncLogScope): List<AttendeeGroupMapping> = track {
        activeProvider.fetchAttendeeGroupMappings(scope)
    }

    override suspend fun fetchMasterListVersion(scope: SyncLogScope): String = track {
        activeProvider.fetchMasterListVersion(scope)
    }

    override suspend fun fetchAttendanceForEvent(
        event: Event,
        startIndex: Int,
        scope: SyncLogScope
    ): PullResult = track {
        activeProvider.fetchAttendanceForEvent(event, startIndex, scope)
    }

    override suspend fun fetchRecentEvents(
        days: Int,
        scope: SyncLogScope
    ): List<Event> = track {
        activeProvider.fetchRecentEvents(days, scope)
    }
}
