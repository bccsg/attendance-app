package sg.org.bcc.attendance.robolectric

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.ui.main.MainListViewModel
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.work.WorkManager
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkInfo
import androidx.test.core.app.ApplicationProvider

import androidx.work.testing.WorkManagerTestInitHelper

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MainListViewModelTest {
    private val repository = mockk<AttendanceRepository>()
    private val authManager = mockk<AuthManager>()
    private lateinit var context: Context
    private val isAuthedFlow = MutableStateFlow(false)
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        
        Dispatchers.setMain(testDispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { authManager.isAuthed } returns isAuthedFlow
        io.mockk.coEvery { repository.syncMasterList() } returns Unit
        io.mockk.coEvery { repository.retrySync() } returns Unit
        io.mockk.coEvery { repository.getUpcomingEvent(any()) } returns null
        io.mockk.coEvery { repository.getLatestEvent() } returns null
        io.mockk.coEvery { repository.getEventById(any()) } returns null
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(android.util.Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `search should filter attendees correctly`() = runTest {
        val attendees = listOf(
            Attendee("1", "John Doe", "John"),
            Attendee("2", "Jane Smith", "Jane")
        )
        every { repository.getAllAttendees() } returns flowOf(attendees)
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getAttendanceRecords(any()) } returns flowOf(emptyList())
        every { repository.getPendingSyncCount() } returns flowOf(0)
        every { repository.getManageableEvents() } returns flowOf(emptyList())
        every { repository.getAllEvents() } returns flowOf(emptyList())
        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllMappings() } returns flowOf(emptyList())
        io.mockk.coEvery { repository.syncMasterList() } returns Unit
        io.mockk.coEvery { repository.isDemoMode() } returns false
        io.mockk.coEvery { repository.purgeOldEvents() } returns Unit

        val viewModel = MainListViewModel(repository, authManager, context)
        
        // Start collecting to activate stateIn
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.attendees.collect()
        }

        viewModel.onSearchQueryChange("John")
        viewModel.attendees.value.size shouldBe 1
        viewModel.attendees.value[0].fullName shouldBe "John Doe"

        viewModel.onSearchQueryChange("z")
        viewModel.attendees.value.size shouldBe 0
        
        job.cancel()
    }

    @Test
    fun `should not sync master list on init if already in demo mode`() = runTest {
        val attendees = listOf(Attendee("D1", "Mickey Mouse"))
        every { repository.getAllAttendees() } returns flowOf(attendees)
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getPendingSyncCount() } returns flowOf(0)
        every { repository.getManageableEvents() } returns flowOf(emptyList())
        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllMappings() } returns flowOf(emptyList())
        every { repository.getAllEvents() } returns flowOf(emptyList())
        
        io.mockk.coEvery { repository.isDemoMode() } returns true
        io.mockk.coEvery { repository.purgeOldEvents() } returns Unit
        io.mockk.coEvery { repository.syncMasterList() } returns Unit

        val viewModel = MainListViewModel(repository, authManager, context)
        
        testScheduler.advanceUntilIdle()
        
        io.mockk.coVerify(exactly = 0) { repository.syncMasterList() }
    }

    @Test
    fun `should sync master list on init if NOT in demo mode`() = runTest {
        isAuthedFlow.value = true
        // Case where database is empty (isDemoMode will be false)
        every { repository.getAllAttendees() } returns flowOf(emptyList())
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getPendingSyncCount() } returns flowOf(0)
        every { repository.getManageableEvents() } returns flowOf(emptyList())
        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllMappings() } returns flowOf(emptyList())
        every { repository.getAllEvents() } returns flowOf(emptyList())
        
        io.mockk.coEvery { repository.isDemoMode() } returns false
        io.mockk.coEvery { repository.purgeOldEvents() } returns Unit
        io.mockk.coEvery { repository.syncMasterList() } returns Unit

        val viewModel = MainListViewModel(repository, authManager, context)
        
        testScheduler.advanceUntilIdle()
        
        io.mockk.coVerify(exactly = 1) { repository.syncMasterList() }
    }

    @Test
    fun `hidePresent should filter out present attendees`() = runTest {
        val attendees = listOf(
            Attendee("1", "John"),
            Attendee("2", "Jane")
        )
        val records = listOf(
            AttendanceRecord("Event", "1", "", "PRESENT", 1000L)
        )
        val events = listOf(Event("Event", "260223 1030 Sunday Service", "2026-02-23", "1030", "cloudId"))
        every { repository.getAllAttendees() } returns flowOf(attendees)
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getAttendanceRecords(any()) } returns flowOf(records)
        every { repository.getPendingSyncCount() } returns flowOf(0)
        every { repository.getManageableEvents() } returns flowOf(events)
        every { repository.getAllEvents() } returns flowOf(events)
        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllMappings() } returns flowOf(emptyList())
        io.mockk.coEvery { repository.syncMasterList() } returns Unit
        io.mockk.coEvery { repository.isDemoMode() } returns false
        io.mockk.coEvery { repository.purgeOldEvents() } returns Unit

        val viewModel = MainListViewModel(repository, authManager, context)
        viewModel.onSwitchEvent("Event")
        
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.attendees.collect()
        }

        // Initially shown
        viewModel.attendees.value.size shouldBe 2
        
        // Toggle hide
        viewModel.onShowAbsentToggle()
        viewModel.attendees.value.size shouldBe 1
        viewModel.attendees.value[0].id shouldBe "2"
        
        job.cancel()
    }

    @Test
    fun `selection mode should work correctly`() = runTest {
        val attendees = listOf(Attendee("1", "John"))
        every { repository.getAllAttendees() } returns flowOf(attendees)
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getAttendanceRecords(any()) } returns flowOf(emptyList())
        every { repository.getPendingSyncCount() } returns flowOf(0)
        every { repository.getManageableEvents() } returns flowOf(emptyList())
        every { repository.getAllEvents() } returns flowOf(emptyList())
        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllMappings() } returns flowOf(emptyList())
        io.mockk.coEvery { repository.syncMasterList() } returns Unit
        io.mockk.coEvery { repository.isDemoMode() } returns false
        io.mockk.coEvery { repository.purgeOldEvents() } returns Unit
        io.mockk.coEvery { repository.replaceQueueWithSelection(any()) } returns Unit

        val viewModel = MainListViewModel(repository, authManager, context)
        
        viewModel.selectedIds.value.size shouldBe 0
        
        viewModel.toggleSelection("1")
        viewModel.selectedIds.value.size shouldBe 1
        viewModel.selectedIds.value.contains("1") shouldBe true
        
        viewModel.confirmSelection()
        io.mockk.coVerify { repository.replaceQueueWithSelection(listOf("1")) }
        viewModel.selectedIds.value.size shouldBe 0
    }

    @Test
    fun `visibility chips should exhibit isolation behavior when both are on`() = runTest {
        val attendees = listOf(
            Attendee("1", "John"),
            Attendee("2", "Jane")
        )
        val records = listOf(
            AttendanceRecord("Event", "1", "", "PRESENT", 1000L)
        )
        val events = listOf(Event("Event", "260223 1030 Sunday Service", "2026-02-23", "1030", "cloudId"))
        every { repository.getAllAttendees() } returns flowOf(attendees)
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getAttendanceRecords(any()) } returns flowOf(records)
        every { repository.getPendingSyncCount() } returns flowOf(0)
        every { repository.getManageableEvents() } returns flowOf(events)
        every { repository.getAllEvents() } returns flowOf(events)
        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllMappings() } returns flowOf(emptyList())
        io.mockk.coEvery { repository.syncMasterList() } returns Unit
        io.mockk.coEvery { repository.isDemoMode() } returns false
        io.mockk.coEvery { repository.purgeOldEvents() } returns Unit

        val viewModel = MainListViewModel(repository, authManager, context)
        viewModel.onSwitchEvent("Event")
        
        // Start collection for reactive flows
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.attendees.collect()
        }
        testScheduler.advanceUntilIdle()

        // Both ON by default
        viewModel.showPresent.value shouldBe true
        viewModel.showAbsent.value shouldBe true

        // Click Present -> Should isolate Present (Absent becomes OFF)
        viewModel.onShowPresentToggle()
        viewModel.showPresent.value shouldBe true
        viewModel.showAbsent.value shouldBe false

        // Click Absent -> Should add it back (Both become ON)
        viewModel.onShowAbsentToggle()
        viewModel.showPresent.value shouldBe true
        viewModel.showAbsent.value shouldBe true

        // Click Absent -> Should isolate Absent (Present becomes OFF)
        viewModel.onShowAbsentToggle()
        viewModel.showPresent.value shouldBe false
        viewModel.showAbsent.value shouldBe true

        // Click Present -> Should add it back
        viewModel.onShowPresentToggle()
        viewModel.showPresent.value shouldBe true
        viewModel.showAbsent.value shouldBe true
        
        job.cancel()
    }

    @Test
    fun `login error should be cleared when dialog is dismissed or login is triggered`() = runTest {
        every { repository.getAllAttendees() } returns flowOf(emptyList())
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getPendingSyncCount() } returns flowOf(0)
        every { repository.getManageableEvents() } returns flowOf(emptyList())
        every { repository.getAllEvents() } returns flowOf(emptyList())
        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllMappings() } returns flowOf(emptyList())
        io.mockk.coEvery { repository.purgeOldEvents() } returns Unit
        io.mockk.coEvery { repository.syncMasterList() } returns Unit

        val viewModel = MainListViewModel(repository, authManager, context)

        // Set an error
        viewModel.onLoginError("Test error")
        viewModel.loginError.value shouldBe "Test error"

        // Dismiss dialog -> Should clear error
        viewModel.setShowCloudStatusDialog(false)
        viewModel.loginError.value shouldBe null

        // Set error again
        viewModel.onLoginError("Another error")
        viewModel.loginError.value shouldBe "Another error"

        // Trigger login -> Should clear error
        viewModel.onLoginTrigger()
        viewModel.loginError.value shouldBe null
    }

    @Test
    fun `handleOAuthCode should exchange code and sync`() = runTest {
        every { repository.getAllAttendees() } returns flowOf(emptyList())
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getPendingSyncCount() } returns flowOf(0)
        every { repository.getManageableEvents() } returns flowOf(emptyList())
        every { repository.getAllEvents() } returns flowOf(emptyList())
        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllMappings() } returns flowOf(emptyList())
        io.mockk.coEvery { repository.purgeOldEvents() } returns Unit
        io.mockk.coEvery { repository.clearAllData() } returns Unit
        
        // Mock successful exchange and sync
        io.mockk.coEvery { authManager.exchangeCodeForTokens("test_code") } returns true
        io.mockk.coEvery { repository.syncMasterListWithDetailedResult() } returns (true to "OK")

        val viewModel = MainListViewModel(repository, authManager, context)
        
        viewModel.handleOAuthCode("test_code")
        
        testScheduler.advanceUntilIdle()
        
        viewModel.loginError.value shouldBe null
        io.mockk.coVerify { authManager.exchangeCodeForTokens("test_code") }
        io.mockk.coVerify { repository.clearAllData() }
        io.mockk.coVerify { repository.syncMasterListWithDetailedResult() }
    }

    @Test
    fun `onLogout should set isSyncing correctly`() = runTest {
        every { repository.getAllAttendees() } returns flowOf(emptyList())
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getPendingSyncCount() } returns flowOf(0)
        every { repository.getManageableEvents() } returns flowOf(emptyList())
        every { repository.getAllEvents() } returns flowOf(emptyList())
        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllMappings() } returns flowOf(emptyList())
        io.mockk.coEvery { repository.purgeOldEvents() } returns Unit
        io.mockk.coEvery { repository.clearAllData() } returns Unit
        io.mockk.coEvery { repository.syncMasterList() } returns Unit
        io.mockk.coEvery { authManager.logout() } returns Unit

        val viewModel = MainListViewModel(repository, authManager, context)
        
        viewModel.onLogout()
        testScheduler.advanceUntilIdle()
        
        io.mockk.coVerify { authManager.logout() }
        io.mockk.coVerify { repository.clearAllData() }
        io.mockk.coVerify { repository.syncMasterList() }
        viewModel.isSyncing.value shouldBe false
    }

    @Test
    fun `handleOAuthCode should show error if exchange fails`() = runTest {
        every { repository.getAllAttendees() } returns flowOf(emptyList())
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getPendingSyncCount() } returns flowOf(0)
        every { repository.getManageableEvents() } returns flowOf(emptyList())
        every { repository.getAllEvents() } returns flowOf(emptyList())
        every { repository.getAllGroups() } returns flowOf(emptyList())
        every { repository.getAllMappings() } returns flowOf(emptyList())
        io.mockk.coEvery { repository.purgeOldEvents() } returns Unit
        
        // Mock failed exchange
        io.mockk.coEvery { authManager.exchangeCodeForTokens("test_code") } returns false

        val viewModel = MainListViewModel(repository, authManager, context)
        
        viewModel.handleOAuthCode("test_code")
        
        testScheduler.advanceUntilIdle()
        
        viewModel.loginError.value?.contains("Failed to exchange code") shouldBe true
    }
}
