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
    fun `syncMasterList should create multiple placeholders for unknown IDs in mappings`() {
        runBlocking {
            // Mock remote data with mappings referencing multiple unknown attendees and groups
            coEvery { cloudProvider.fetchMasterListVersion(any()) } returns "v2"
            coEvery { cloudProvider.fetchMasterAttendees(any()) } returns emptyList()
            coEvery { cloudProvider.fetchMasterGroups(any()) } returns emptyList()
            coEvery { cloudProvider.fetchAttendeeGroupMappings(any()) } returns listOf(
                AttendeeGroupMapping("UnknownA1", "UnknownG1"),
                AttendeeGroupMapping("UnknownA2", "UnknownG1"),
                AttendeeGroupMapping("UnknownA1", "UnknownG2")
            )
            coEvery { cloudProvider.fetchRecentEvents(30, any()) } returns emptyList()

            repository.syncMasterListWithDetailedResult()

            val attendees = db.attendeeDao().getAllAttendees().first().sortedBy { it.id }
            attendees.size shouldBe 2
            attendees[0].id shouldBe "UnknownA1"
            attendees[0].notExistOnCloud shouldBe true
            attendees[1].id shouldBe "UnknownA2"
            attendees[1].notExistOnCloud shouldBe true

            val groups = db.groupDao().getAllGroups().first().sortedBy { it.groupId }
            groups.size shouldBe 2
            groups[0].groupId shouldBe "UnknownG1"
            groups[0].notExistOnCloud shouldBe true
            groups[1].groupId shouldBe "UnknownG2"
            groups[1].notExistOnCloud shouldBe true
        }
    }

    @Test
    fun `syncAttendanceForEvent should create multiple placeholders from records`() {
        runBlocking {
            val event = Event(id = "E1", title = "260225 1000 Test", date = "2026-02-25", time = "1000")
            db.eventDao().insertAll(listOf(event))

            // Mock remote attendance with multiple unknown attendees
            coEvery { cloudProvider.fetchAttendanceForEvent(any(), any(), any()) } returns sg.org.bcc.attendance.data.remote.PullResult(
                records = listOf(
                    AttendanceRecord("E1", "UnknownA1", "Name 1", "PRESENT", 100L),
                    AttendanceRecord("E1", "UnknownA2", "Name 2", "PRESENT", 200L),
                    AttendanceRecord("E1", "UnknownA1", "Name 1 Updated", "PRESENT", 300L)
                ),
                lastRowIndex = 3
            )

            repository.syncAttendanceForEvent(event)

            val attendees = db.attendeeDao().getAllAttendees().first().sortedBy { it.id }
            attendees.size shouldBe 2
            attendees[0].id shouldBe "UnknownA1"
            attendees[0].fullName shouldBe "Name 1 Updated" // Latest name should be picked
            attendees[0].notExistOnCloud shouldBe true
            attendees[1].id shouldBe "UnknownA2"
            attendees[1].fullName shouldBe "Name 2"
            attendees[1].notExistOnCloud shouldBe true
        }
    }

    @Test
    fun `syncMasterList status message should use correct Missing on cloud terminology`() {
        runBlocking {
            db.attendeeDao().insertAll(listOf(Attendee("A1", "Local Only")))
            db.groupDao().insertAll(listOf(Group("G1", "Local Only")))

            coEvery { cloudProvider.fetchMasterListVersion(any()) } returns "v2"
            coEvery { cloudProvider.fetchMasterAttendees(any()) } returns emptyList()
            coEvery { cloudProvider.fetchMasterGroups(any()) } returns emptyList()
            coEvery { cloudProvider.fetchAttendeeGroupMappings(any()) } returns emptyList()
            coEvery { cloudProvider.fetchRecentEvents(30, any()) } returns emptyList()

            val (_, status) = repository.syncMasterListWithDetailedResult()
            
            status shouldBe "Attendees: OK (0), Missing on cloud: 1\nGroups: OK (0), Missing on cloud: 1\nMappings: OK (0)\nEvents Discovery: OK (0)\nAttendance: Skipped (No event selected)"
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
