package sg.org.bcc.attendance.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
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
import sg.org.bcc.attendance.data.local.entities.*
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.data.repository.AttendanceRepository

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import sg.org.bcc.attendance.data.remote.PushResult
import sg.org.bcc.attendance.sync.SyncWorker

@RunWith(RobolectricTestRunner::class)
class MissingOnCloudSyncTest {
    private lateinit var context: Context
    private lateinit var db: AttendanceDatabase
    private lateinit var repository: AttendanceRepository
    private lateinit var cloudProvider: AttendanceCloudProvider
    private lateinit var authManager: AuthManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        cloudProvider = mockk(relaxed = true)
        authManager = mockk(relaxed = true)
        
        every { authManager.isDemoMode } returns MutableStateFlow(false)
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
            sg.org.bcc.attendance.util.time.TrueTimeProvider(),
            mockk(relaxed = true)
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `syncMasterList should mark local attendees as missing if not in remote`() {
        runBlocking {
            // Setup local data
            db.attendeeDao().insertAll(listOf(
                Attendee("A1", "Existing"),
                Attendee("A2", "To be missing")
            ))

            // Mock remote data (only A1 exists)
            coEvery { cloudProvider.fetchMasterListVersion(any()) } returns "v2"
            coEvery { cloudProvider.fetchMasterAttendees(any()) } returns listOf(
                Attendee("A1", "Existing")
            )
            coEvery { cloudProvider.fetchMasterGroups(any()) } returns emptyList()
            coEvery { cloudProvider.fetchAttendeeGroupMappings(any()) } returns emptyList()
            coEvery { cloudProvider.fetchRecentEvents(30, any()) } returns emptyList()

            repository.syncMasterListWithDetailedResult()

            val allAttendees = db.attendeeDao().getAllAttendees().first()
            allAttendees.size shouldBe 2
            allAttendees.find { it.id == "A1" }?.notExistOnCloud shouldBe false
            allAttendees.find { it.id == "A2" }?.notExistOnCloud shouldBe true
        }
    }

    @Test
    fun `syncMasterList should mark local groups as missing if not in remote`() {
        runBlocking {
            // Setup local data
            db.groupDao().insertAll(listOf(
                Group("G1", "Existing"),
                Group("G2", "To be missing")
            ))

            // Mock remote data (only G1 exists)
            coEvery { cloudProvider.fetchMasterListVersion(any()) } returns "v2"
            coEvery { cloudProvider.fetchMasterAttendees(any()) } returns emptyList()
            coEvery { cloudProvider.fetchMasterGroups(any()) } returns listOf(
                Group("G1", "Existing")
            )
            coEvery { cloudProvider.fetchAttendeeGroupMappings(any()) } returns emptyList()
            coEvery { cloudProvider.fetchRecentEvents(30, any()) } returns emptyList()

            repository.syncMasterListWithDetailedResult()

            val allGroups = db.groupDao().getAllGroups().first()
            allGroups.size shouldBe 2
            allGroups.find { it.groupId == "G1" }?.notExistOnCloud shouldBe false
            allGroups.find { it.groupId == "G2" }?.notExistOnCloud shouldBe true
        }
    }

    @Test
    fun `syncMasterList should upsert placeholders for unknown IDs in mappings`() {
        runBlocking {
            // Mock remote data with mapping referencing unknown attendee and group
            coEvery { cloudProvider.fetchMasterListVersion(any()) } returns "v2"
            coEvery { cloudProvider.fetchMasterAttendees(any()) } returns emptyList()
            coEvery { cloudProvider.fetchMasterGroups(any()) } returns emptyList()
            coEvery { cloudProvider.fetchAttendeeGroupMappings(any()) } returns listOf(
                AttendeeGroupMapping("UnknownA", "UnknownG")
            )
            coEvery { cloudProvider.fetchRecentEvents(30, any()) } returns emptyList()

            repository.syncMasterListWithDetailedResult()

            val attendees = db.attendeeDao().getAllAttendees().first()
            attendees.size shouldBe 1
            attendees[0].id shouldBe "UnknownA"
            attendees[0].notExistOnCloud shouldBe true

            val groups = db.groupDao().getAllGroups().first()
            groups.size shouldBe 1
            groups[0].groupId shouldBe "UnknownG"
            groups[0].notExistOnCloud shouldBe true
        }
    }

    @Test
    fun `syncAttendanceForEvent should upsert placeholders for unknown IDs in records`() {
        runBlocking {
            val event = Event(id = "E1", title = "2026-02-25 1000 Test", date = "2026-02-25", time = "1000")
            db.eventDao().insertAll(listOf(event))

            // Mock remote attendance with unknown attendee
            coEvery { cloudProvider.fetchAttendanceForEvent(any(), any(), any()) } returns sg.org.bcc.attendance.data.remote.PullResult(
                records = listOf(
                    AttendanceRecord("E1", "UnknownA", "Unknown FullName", "PRESENT", 123456789L)
                ),
                lastRowIndex = 1
            )

            repository.syncAttendanceForEvent(event)

            val attendees = db.attendeeDao().getAllAttendees().first()
            attendees.size shouldBe 1
            attendees[0].id shouldBe "UnknownA"
            attendees[0].notExistOnCloud shouldBe true
        }
    }

    @Test
    fun `purgeAllMissingFromCloud should clear marked entities`() {
        runBlocking {
            db.attendeeDao().insertAll(listOf(
                Attendee("A1", "Normal", notExistOnCloud = false),
                Attendee("A2", "Missing", notExistOnCloud = true)
            ))
            db.groupDao().insertAll(listOf(
                Group("G1", "Normal", notExistOnCloud = false),
                Group("G2", "Missing", notExistOnCloud = true)
            ))

            repository.purgeAllMissingFromCloud()

            db.attendeeDao().getAllAttendees().first().size shouldBe 1
            db.attendeeDao().getAllAttendees().first()[0].id shouldBe "A1"

            db.groupDao().getAllGroups().first().size shouldBe 1
            db.groupDao().getAllGroups().first()[0].groupId shouldBe "G1"
        }
    }

    @Test
    fun `syncMasterList should mark local events as missing if not in remote`() {
        runBlocking {
            // Setup local data (within 30 day window)
            val today = java.time.LocalDate.now()
            val dateStr = today.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            val e1 = Event(id = "E1", title = "260225 1000 E1", date = dateStr, time = "1000", cloudEventId = "c1")
            val e2 = Event(id = "E2", title = "260225 1100 E2", date = dateStr, time = "1100", cloudEventId = "c2")
            db.eventDao().insertAll(listOf(e1, e2))

            // Mock remote data (only E1 exists)
            coEvery { cloudProvider.fetchRecentEvents(30, any()) } returns listOf(
                e1.copy(id = "remote_e1") // ID doesn't matter for merge, title/cloudEventId does
            )

            repository.syncMasterListWithDetailedResult()

            val allEvents = db.eventDao().getAllEvents().first()
            allEvents.size shouldBe 2
            allEvents.find { it.id == "E1" }?.notExistOnCloud shouldBe false
            allEvents.find { it.id == "E2" }?.notExistOnCloud shouldBe true
        }
    }

    @Test
    fun `resolveEventDeleteLocally should clear jobs, attendance and event`() {
        runBlocking {
            val eventId = "E1"
            db.eventDao().insert(Event(id = eventId, title = "260225 1000 E1", date = "2026-02-25", time = "1000"))
            db.attendanceDao().insert(AttendanceRecord(eventId, "A1", "John", "PRESENT", 123L))
            db.syncJobDao().insert(SyncJob(eventId = eventId, payloadJson = "[]"))

            repository.resolveEventDeleteLocally(eventId)

            db.eventDao().getEventById(eventId) shouldBe null
            db.attendanceDao().getAttendanceForEvent(eventId).size shouldBe 0
            db.syncJobDao().getPendingCount() shouldBe 0
        }
    }

    @Test
    fun `SyncWorker should mark event as missing on cloud if push fails due to missing sheet`() {
        runBlocking {
            val eventId = "E1"
            db.eventDao().insert(Event(id = eventId, title = "260225 1000 E1", date = "2026-02-25", time = "1000"))
            
            val job = SyncJob(eventId = eventId, payloadJson = """[{"id":"a1","name":"John","state":"PRESENT","time":1000}]""")
            db.syncJobDao().insert(job)
            
            coEvery { cloudProvider.pushAttendance(any(), any(), any(), any()) } returns PushResult.Error("Cloud worksheet not found", isRetryable = false)
            
            val worker = TestListenableWorkerBuilder<SyncWorker>(context)
                .setWorkerFactory(object : androidx.work.WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: androidx.work.WorkerParameters): ListenableWorker {
                        return SyncWorker(appContext, workerParameters, db.syncJobDao(), db.eventDao(), db.syncLogDao(), cloudProvider, authManager)
                    }
                })
                .build()
            
            worker.doWork() shouldBe ListenableWorker.Result.retry()
            
            val updatedEvent = db.eventDao().getEventById(eventId)
            updatedEvent?.notExistOnCloud shouldBe true
        }
    }
}
