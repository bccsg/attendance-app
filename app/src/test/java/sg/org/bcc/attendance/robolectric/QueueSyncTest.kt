package sg.org.bcc.attendance.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sg.org.bcc.attendance.data.local.AttendanceDatabase
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.PersistentQueue
import sg.org.bcc.attendance.data.repository.AttendanceRepository

@RunWith(RobolectricTestRunner::class)
class QueueSyncTest {
    private lateinit var db: AttendanceDatabase
    private lateinit var repository: AttendanceRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
            io.mockk.mockk(relaxed = true), // cloudProvider
            io.mockk.mockk(relaxed = true), // authManager
            sg.org.bcc.attendance.util.time.TrueTimeProvider(),
            io.mockk.mockk(relaxed = true) // syncScheduler
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `syncQueue should move items from persistent_queue to sync_jobs and archive`() {
        runBlocking {
            // Setup: Attendees and Queue
            val attendees = listOf(
                Attendee("A01", "John Doe", "John"),
                Attendee("A02", "Jane Smith", "Jane")
            )
            db.attendeeDao().insertAll(attendees)
            
            db.persistentQueueDao().insertAll(listOf(
                PersistentQueue("A01", isLater = false),
                PersistentQueue("A02", isLater = true)
            ))

            // Act: Sync
            val eventId = "event-123"
            repository.syncQueue(eventId)

            // Assert: SyncJob created
            db.syncJobDao().getPendingCount() shouldBe 1

            // Assert: Archive created
            val archives = repository.getArchives().first()
            archives.size shouldBe 1
            archives[0].eventId shouldBe eventId
            archives[0].dataJson shouldNotBe null
        }
    }

    @Test
    fun `archive should maintain 25-slot FIFO`() {
        runBlocking {
            db.attendeeDao().insertAll(listOf(Attendee("A01", "John Doe", "John")))
            
            // Perform 30 syncs
            repeat(30) { i ->
                db.persistentQueueDao().insertAll(listOf(PersistentQueue("A01")))
                repository.syncQueue("event-$i")
            }

            // Assert: Only 25 archives remain
            val archives = repository.getArchives().first()
            archives.size shouldBe 25
            // Oldest should be "event-5" because 0-4 were deleted
            archives.last().eventId shouldBe "event-5"
            archives.first().eventId shouldBe "event-29"
        }
    }

    @Test
    fun `restoreFromArchive should append missing items to queue`() {
        runBlocking {
            db.attendeeDao().insertAll(listOf(
                Attendee("A01", "John"),
                Attendee("A02", "Jane")
            ))

            // 1. Sync A01 to create an archive
            db.persistentQueueDao().insertAll(listOf(PersistentQueue("A01")))
            repository.syncQueue("Archive Event")

            // 2. Add A02 to current queue manually
            db.persistentQueueDao().insertAll(listOf(PersistentQueue("A02")))

            // 3. Restore from archive
            val archive = repository.getArchives().first()[0]
            repository.restoreFromArchive(archive.id)

            // Assert: Queue should have both A01 and A02
            val items = repository.getQueueItems().first()
            items.size shouldBe 2
            items.any { it.attendee.id == "A01" } shouldBe true
            items.any { it.attendee.id == "A02" } shouldBe true
        }
    }
}
