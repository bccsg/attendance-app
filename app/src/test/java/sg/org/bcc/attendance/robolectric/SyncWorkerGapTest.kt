package sg.org.bcc.attendance.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sg.org.bcc.attendance.data.local.AttendanceDatabase
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.data.local.entities.SyncJob
import sg.org.bcc.attendance.data.remote.AttendanceCloudProvider
import sg.org.bcc.attendance.data.remote.AuthManager
import sg.org.bcc.attendance.data.remote.PushResult
import sg.org.bcc.attendance.sync.SyncWorker

@RunWith(RobolectricTestRunner::class)
class SyncWorkerGapTest {
    private lateinit var context: Context
    private lateinit var db: AttendanceDatabase
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
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `SyncWorker should update lastProcessedRowIndex on clean push`() {
        runBlocking {
            val event = Event(id = "e1", title = "260225 0900 Test", date = "2026-02-25", time = "0900", lastProcessedRowIndex = 10)
            db.eventDao().insert(event)
            
            val job = SyncJob(eventId = "e1", payloadJson = """[{"id":"a1","name":"John","state":"PRESENT","time":1000}]""")
            db.syncJobDao().insert(job)
            
            // M=10, K=1. Cloud returns N=11 (Clean Push)
            coEvery { cloudProvider.pushAttendance(any(), any(), any(), any()) } returns PushResult.Success(11)
            
            val worker = TestListenableWorkerBuilder<SyncWorker>(context)
                .setWorkerFactory(object : androidx.work.WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: androidx.work.WorkerParameters): ListenableWorker {
                        return SyncWorker(appContext, workerParameters, db.syncJobDao(), db.eventDao(), db.syncLogDao(), cloudProvider, authManager)
                    }
                })
                .build()
            
            worker.doWork() shouldBe ListenableWorker.Result.success()
            
            val updatedEvent = db.eventDao().getEventById("e1")
            updatedEvent?.lastProcessedRowIndex shouldBe 11
        }
    }

    @Test
    fun `SyncWorker should NOT update lastProcessedRowIndex if gap detected`() {
        runBlocking {
            val event = Event(id = "e1", title = "260225 0900 Test", date = "2026-02-25", time = "0900", lastProcessedRowIndex = 10)
            db.eventDao().insert(event)
            
            val job = SyncJob(eventId = "e1", payloadJson = """[{"id":"a1","name":"John","state":"PRESENT","time":1000}]""")
            db.syncJobDao().insert(job)
            
            // M=10, K=1. Cloud returns N=12 (Gap detected: someone else pushed 1 row)
            coEvery { cloudProvider.pushAttendance(any(), any(), any(), any()) } returns PushResult.Success(12)
            
            val worker = TestListenableWorkerBuilder<SyncWorker>(context)
                .setWorkerFactory(object : androidx.work.WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: androidx.work.WorkerParameters): ListenableWorker {
                        return SyncWorker(appContext, workerParameters, db.syncJobDao(), db.eventDao(), db.syncLogDao(), cloudProvider, authManager)
                    }
                })
                .build()
            
            worker.doWork() shouldBe ListenableWorker.Result.success()
            
            val updatedEvent = db.eventDao().getEventById("e1")
            updatedEvent?.lastProcessedRowIndex shouldBe 10 // Remained at M
        }
    }
}
