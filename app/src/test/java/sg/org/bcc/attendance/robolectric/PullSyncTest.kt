package sg.org.bcc.attendance.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
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
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.sync.SyncScheduler

@RunWith(RobolectricTestRunner::class)
class PullSyncTest {
    private lateinit var context: Context
    private lateinit var db: AttendanceDatabase
    private lateinit var repository: AttendanceRepository
    private lateinit var cloudProvider: AttendanceCloudProvider
    private lateinit var authManager: AuthManager
    private lateinit var syncScheduler: SyncScheduler

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
            db.attendeeDao(),
            db.attendanceDao(),
            db.persistentQueueDao(),
            db.syncJobDao(),
            db.queueArchiveDao(),
            db.eventDao(),
            db.groupDao(),
            db.attendeeGroupMappingDao(),
            cloudProvider,
            authManager,
            sg.org.bcc.attendance.util.time.TrueTimeProvider(),
            mockk(relaxed = true) // syncScheduler
        )
        
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        syncScheduler = SyncScheduler(context)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `schedulePeriodicPull should enqueue periodic work`() {
        syncScheduler.schedulePeriodicPull()
        
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SyncScheduler.PULL_WORK_NAME)
            .get()
            
        workInfos.size shouldBe 1
        workInfos[0].state shouldBe WorkInfo.State.ENQUEUED
    }

    @Test
    fun `syncMasterListWithDetailedResult should skip if jobs are pending`() {
        runBlocking {
            // Setup: Add a pending sync job
            db.syncJobDao().insert(sg.org.bcc.attendance.data.local.entities.SyncJob(
                eventId = "e1",
                payloadJson = "[]"
            ))

            val (success, status) = repository.syncMasterListWithDetailedResult()
            
            success shouldBe false
            status.contains("Skipped pull due to pending sync jobs") shouldBe true
        }
    }

    @Test
    fun `syncMasterListWithDetailedResult should pull all data`() {
        runBlocking {
            val attendees = listOf(Attendee("A1", "John"))
            coEvery { cloudProvider.fetchMasterAttendees() } returns attendees
            coEvery { cloudProvider.fetchMasterGroups() } returns emptyList()
            coEvery { cloudProvider.fetchAttendeeGroupMappings() } returns emptyList()
            coEvery { cloudProvider.fetchRecentEvents(30) } returns emptyList()

            val (success, status) = repository.syncMasterListWithDetailedResult()
            
            success shouldBe true
            status.contains("Attendees: OK (1)") shouldBe true
            
            val localAttendees: List<Attendee> = db.attendeeDao().getAllAttendees().first()
            localAttendees.size shouldBe 1
            localAttendees[0].id shouldBe "A1"
        }
    }
}
