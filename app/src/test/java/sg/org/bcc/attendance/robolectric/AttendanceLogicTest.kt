package sg.org.bcc.attendance.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sg.org.bcc.attendance.data.local.AttendanceDatabase
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.data.remote.PullResult
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.util.time.TimeProvider

@RunWith(RobolectricTestRunner::class)
class AttendanceLogicTest {
    private lateinit var context: Context
    private lateinit var db: AttendanceDatabase
    private lateinit var repository: AttendanceRepository
    private lateinit var cloudProvider: AttendanceCloudProvider
    private lateinit var authManager: AuthManager
    private lateinit var timeProvider: TimeProvider

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        cloudProvider = mockk(relaxed = true)
        authManager = mockk(relaxed = true)
        timeProvider = mockk(relaxed = true)
        
        io.mockk.every { authManager.isDemoMode } returns MutableStateFlow(false)
        coEvery { authManager.isTokenExpired() } returns false
        coEvery { authManager.silentRefresh() } returns true
        
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
            timeProvider,
            mockk(relaxed = true)
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `batch reduction should only keep the latest record per attendee`() {
        runBlocking {
            val eventId = "e1"
            val records = listOf(
                AttendanceRecord(eventId, "a1", state = "PRESENT", timestamp = 1000L),
                AttendanceRecord(eventId, "a1", state = "ABSENT", timestamp = 2000L), // Newer
                AttendanceRecord(eventId, "a2", state = "PRESENT", timestamp = 1500L)
            )

            db.attendanceDao().upsertAllIfNewer(records)

            val local = db.attendanceDao().getAttendanceForEvent(eventId)
            local.size shouldBe 2
            local.find { it.attendeeId == "a1" }?.state shouldBe "ABSENT"
            local.find { it.attendeeId == "a1" }?.timestamp shouldBe 2000L
            local.find { it.attendeeId == "a2" }?.state shouldBe "PRESENT"
        }
    }

    @Test
    fun `differential pull should use lastProcessedRowIndex and update it`() {
        runBlocking {
            val event = Event(id = "e1", title = "260225 0900 Test", date = "2026-02-25", time = "0900", lastProcessedRowIndex = 5)
            db.eventDao().insert(event)

            val remoteRecords = listOf(
                AttendanceRecord(event.id, "a1", state = "PRESENT", timestamp = 3000L)
            )
            // Cloud says last row is now 6
            coEvery { cloudProvider.fetchAttendanceForEvent(any(), 5, any()) } returns PullResult(remoteRecords, 6)

            repository.syncAttendanceForEvent(event)

            // Verify local event is updated
            val updatedEvent = db.eventDao().getEventById(event.id)
            updatedEvent?.lastProcessedRowIndex shouldBe 6

            // Verify record is inserted
            val localRecords = db.attendanceDao().getAttendanceForEvent(event.id)
            localRecords.size shouldBe 1
            localRecords[0].attendeeId shouldBe "a1"
        }
    }

    @Test
    fun `syncAttendanceForEvent should skip update if no new rows`() {
        runBlocking {
            val event = Event(id = "e1", title = "260225 0900 Test", date = "2026-02-25", time = "0900", lastProcessedRowIndex = 10)
            db.eventDao().insert(event)

            // Cloud says still 10
            coEvery { cloudProvider.fetchAttendanceForEvent(any(), 10, any()) } returns PullResult(emptyList(), 10)

            repository.syncAttendanceForEvent(event)

            val updatedEvent = db.eventDao().getEventById(event.id)
            updatedEvent?.lastProcessedRowIndex shouldBe 10
        }
    }
}
