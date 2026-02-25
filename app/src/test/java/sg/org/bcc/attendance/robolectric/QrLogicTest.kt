package sg.org.bcc.attendance.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sg.org.bcc.attendance.data.local.AttendanceDatabase
import sg.org.bcc.attendance.data.local.entities.Attendee
import sg.org.bcc.attendance.data.local.entities.AttendeeGroupMapping
import sg.org.bcc.attendance.data.local.entities.Group
import sg.org.bcc.attendance.data.repository.AttendanceRepository
import sg.org.bcc.attendance.util.qr.QrInfo

@RunWith(RobolectricTestRunner::class)
class QrLogicTest {
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
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `should queue all group members if group exists`() {
        runBlocking {
            // Setup group and members
            val group = Group("G1", "Grace Group")
            val a1 = Attendee("A1", "Attendee 1")
            val a2 = Attendee("A2", "Attendee 2")
            db.groupDao().insertAll(listOf(group))
            db.attendeeDao().insertAll(listOf(a1, a2))
            db.attendeeGroupMappingDao().insertAll(listOf(
                AttendeeGroupMapping("A1", "G1"),
                AttendeeGroupMapping("A2", "G1")
            ))

            val info = QrInfo(groupId = "G1")
            val message = repository.processQrInfo(info)

            message shouldBe "Queued 2 members from Grace Group"
            val queue = db.persistentQueueDao().getQueue().first()
            queue.size shouldBe 2
            queue.map { it.attendeeId }.toSet() shouldBe setOf("A1", "A2")
        }
    }

    @Test
    fun `should auto-create attendee if not exists`() {
        runBlocking {
            val info = QrInfo(personId = "P999", personName = "New Guy")
            val message = repository.processQrInfo(info)

            message shouldBe "Queued New Guy"
            val attendee = db.attendeeDao().getAttendeeById("P999")
            attendee shouldBe Attendee("P999", "New Guy", notExistOnCloud = true)
            
            val queue = db.persistentQueueDao().getQueue().first()
            queue.size shouldBe 1
            queue[0].attendeeId shouldBe "P999"
        }
    }

    @Test
    fun `should fallback to individual if group not found`() {
        runBlocking {
            val attendee = Attendee("A1", "Attendee 1")
            db.attendeeDao().insertAll(listOf(attendee))

            // Info has both, but G999 doesn't exist
            val info = QrInfo(personId = "A1", groupId = "G999")
            val message = repository.processQrInfo(info)

            message shouldBe "Queued Attendee 1"
            val queue = db.persistentQueueDao().getQueue().first()
            queue.size shouldBe 1
            queue[0].attendeeId shouldBe "A1"
        }
    }

    @Test
    fun `should handle already in queue`() {
        runBlocking {
            val attendee = Attendee("A1", "Attendee 1")
            db.attendeeDao().insertAll(listOf(attendee))
            repository.addToQueue("A1")

            val info = QrInfo(personId = "A1")
            val message = repository.processQrInfo(info)

            message shouldBe "Attendee 1 already in queue"
            db.persistentQueueDao().getQueue().first().size shouldBe 1
        }
    }
}
