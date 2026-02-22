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
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.ui.queue.QueueViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {
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
    fun `presentIds should filter and collect IDs with state PRESENT for current event`() = runTest {
        val eventId = "event-123"
        val records = listOf(
            AttendanceRecord(eventId, "A01", "PRESENT", 1000L),
            AttendanceRecord(eventId, "A02", "ABSENT", 1100L),
            AttendanceRecord(eventId, "A03", "PRESENT", 1200L)
        )
        
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getAllAttendees() } returns flowOf(emptyList())
        every { repository.getAttendanceRecords(eventId) } returns flowOf(records)
        every { repository.getManageableEvents() } returns flowOf(emptyList())

        val viewModel = QueueViewModel(repository)
        
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.presentIds.collect { }
        }

        // Before setting event ID
        viewModel.presentIds.value shouldBe emptySet()

        // After setting event ID
        viewModel.setEventId(eventId)
        
        viewModel.presentIds.value shouldBe setOf("A01", "A03")
        
        job.cancel()
    }

    @Test
    fun `presentIds should be empty when eventId is null`() = runTest {
        val eventId = "event-123"
        val records = listOf(
            AttendanceRecord(eventId, "A01", "PRESENT", 1000L)
        )
        
        every { repository.getQueueItems() } returns flowOf(emptyList())
        every { repository.getAllAttendees() } returns flowOf(emptyList())
        every { repository.getAttendanceRecords(any()) } returns flowOf(records)
        every { repository.getManageableEvents() } returns flowOf(emptyList())

        val viewModel = QueueViewModel(repository)
        
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.presentIds.collect { }
        }

        viewModel.setEventId(eventId)
        viewModel.presentIds.value shouldBe setOf("A01")

        viewModel.setEventId(null)
        viewModel.presentIds.value shouldBe emptySet()
        
        job.cancel()
    }
}
