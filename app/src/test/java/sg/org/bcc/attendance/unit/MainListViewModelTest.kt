package sg.org.bcc.attendance.unit

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.ui.main.MainListViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class MainListViewModelTest {
    private val repository = mockk<AttendanceRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
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

        val viewModel = MainListViewModel(repository)
        
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

        val viewModel = MainListViewModel(repository)
        
        testScheduler.advanceUntilIdle()
        
        io.mockk.coVerify(exactly = 0) { repository.syncMasterList() }
    }

    @Test
    fun `should sync master list on init if NOT in demo mode`() = runTest {
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

        val viewModel = MainListViewModel(repository)
        
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
            AttendanceRecord("Event", "1", "PRESENT", 1000L)
        )
        val events = listOf(Event("Event", "260223 1030 Sunday Service", "cloudId"))
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

        val viewModel = MainListViewModel(repository)
        
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

        val viewModel = MainListViewModel(repository)
        
        viewModel.selectedIds.value.size shouldBe 0
        
        viewModel.toggleSelection("1")
        viewModel.selectedIds.value.size shouldBe 1
        viewModel.selectedIds.value.contains("1") shouldBe true
        
        viewModel.confirmSelection()
        io.mockk.coVerify { repository.replaceQueueWithSelection(listOf("1")) }
        viewModel.selectedIds.value.size shouldBe 0
    }
}
