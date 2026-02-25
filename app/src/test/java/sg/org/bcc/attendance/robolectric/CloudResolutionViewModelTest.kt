package sg.org.bcc.attendance.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sg.org.bcc.attendance.data.local.AttendanceDatabase
import sg.org.bcc.attendance.data.local.entities.*
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.ui.main.CloudResolutionViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CloudResolutionViewModelTest {
    private lateinit var context: Context
    private lateinit var db: AttendanceDatabase
    private lateinit var repository: AttendanceRepository
    private lateinit var viewModel: CloudResolutionViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        val cloudProvider = mockk<AttendanceCloudProvider>(relaxed = true)
        val authManager = mockk<AuthManager>(relaxed = true)
        
        every { authManager.isDemoMode } returns MutableStateFlow(false)
        Dispatchers.setMain(testDispatcher)
        
        repository = AttendanceRepository(
            context,
            db.attendeeDao(),
            db.attendanceDao(),
            db.persistentQueueDao(),
            db.syncJobDao(),
            db.queueArchiveDao(),
            db.eventDao(),
            db.groupDao(),
            db.attendeeGroupMappingDao(),
            db.syncLogDao(),
            cloudProvider,
            authManager,
            sg.org.bcc.attendance.util.time.TrueTimeProvider(),
            mockk(relaxed = true)
        )
        viewModel = CloudResolutionViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `isAttendeeInUse should return true if attendee has attendance records`() = runTest {
        db.attendeeDao().insertAll(listOf(Attendee("A1", "John")))
        db.attendanceDao().insert(AttendanceRecord("E1", "A1", "John", "PRESENT", 1000L))
        
        viewModel.isAttendeeInUse("A1") shouldBe true
    }

    @Test
    fun `isAttendeeInUse should return true if attendee has group mappings`() = runTest {
        db.attendeeDao().insertAll(listOf(Attendee("A1", "John")))
        db.groupDao().insertAll(listOf(Group("G1", "Group 1")))
        db.attendeeGroupMappingDao().insertAll(listOf(AttendeeGroupMapping("A1", "G1")))
        
        viewModel.isAttendeeInUse("A1") shouldBe true
    }

    @Test
    fun `isAttendeeInUse should return false if attendee is not in use`() = runTest {
        db.attendeeDao().insertAll(listOf(Attendee("A1", "John")))
        
        viewModel.isAttendeeInUse("A1") shouldBe false
    }

    @Test
    fun `isGroupInUse should return true if group has linked attendees`() = runTest {
        db.attendeeDao().insertAll(listOf(Attendee("A1", "John")))
        db.groupDao().insertAll(listOf(Group("G1", "Group 1")))
        db.attendeeGroupMappingDao().insertAll(listOf(AttendeeGroupMapping("A1", "G1")))
        
        viewModel.isGroupInUse("G1") shouldBe true
    }

    @Test
    fun `isGroupInUse should return false if group has no linked attendees`() = runTest {
        db.groupDao().insertAll(listOf(Group("G1", "Group 1")))
        
        viewModel.isGroupInUse("G1") shouldBe false
    }

    @Test
    fun `removeAttendee should delete attendee from local database`() = runTest {
        db.attendeeDao().insertAll(listOf(Attendee("A1", "John", notExistOnCloud = true)))
        
        viewModel.removeAttendee("A1") {}
        advanceUntilIdle()
        
        db.attendeeDao().getAllAttendees().first().any { it.id == "A1" } shouldBe false
    }

    @Test
    fun `removeGroup should delete group from local database`() = runTest {
        db.groupDao().insertAll(listOf(Group("G1", "Group 1", notExistOnCloud = true)))
        
        viewModel.removeGroup("G1") {}
        advanceUntilIdle()
        
        db.groupDao().getAllGroups().first().any { it.groupId == "G1" } shouldBe false
    }
}
