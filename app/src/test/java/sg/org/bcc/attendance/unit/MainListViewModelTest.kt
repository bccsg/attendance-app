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
    fun `hidePresent should filter out present attendees`() = runTest {
        val attendees = listOf(
            Attendee("1", "John"),
            Attendee("2", "Jane")
        )
        val records = listOf(
            sg.org.bcc.attendance.data.local.entities.AttendanceRecord("Event", "1", "PRESENT", 1000L)
        )
        every { repository.getAllAttendees() } returns flowOf(attendees)
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getAttendanceRecords(any()) } returns flowOf(records)

        val viewModel = MainListViewModel(repository)
        
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.attendees.collect()
        }

        // Initially shown
        viewModel.attendees.value.size shouldBe 2
        
        // Toggle hide
        viewModel.onHidePresentToggle()
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
        io.mockk.coEvery { repository.addToQueue(any()) } returns Unit

        val viewModel = MainListViewModel(repository)
        
        viewModel.selectedIds.value.size shouldBe 0
        
        viewModel.toggleSelection("1")
        viewModel.selectedIds.value.size shouldBe 1
        viewModel.selectedIds.value.contains("1") shouldBe true
        
        viewModel.addSelectedToQueue()
        io.mockk.coVerify { repository.addToQueue("1") }
        viewModel.selectedIds.value.size shouldBe 0
    }
}
