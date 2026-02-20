package sg.org.bcc.attendance.robolectric

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sg.org.bcc.attendance.data.local.AttendanceDatabase
import sg.org.bcc.attendance.data.local.dao.AttendanceDao
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class AttendanceDaoTest {
    private lateinit var db: AttendanceDatabase
    private lateinit var dao: AttendanceDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.attendanceDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `upsertIfNewer should handle updates correctly`() {
        runBlocking {
            val event = "2602221030:sunday service"
            val attendeeId = "A001"
            
            // Initial insert
            val record = AttendanceRecord(event, attendeeId, "PRESENT", 1000L)
            dao.upsertIfNewer(record)
            
            var result = dao.getRecord(event, attendeeId)
            result?.state shouldBe "PRESENT"
            result?.timestamp shouldBe 1000L

            // Older update ignored
            val olderRecord = AttendanceRecord(event, attendeeId, "ABSENT", 500L)
            dao.upsertIfNewer(olderRecord)
            
            result = dao.getRecord(event, attendeeId)
            result?.state shouldBe "PRESENT"
            result?.timestamp shouldBe 1000L

            // Newer update applied
            val newerRecord = AttendanceRecord(event, attendeeId, "ABSENT", 2000L)
            dao.upsertIfNewer(newerRecord)
            
            result = dao.getRecord(event, attendeeId)
            result?.state shouldBe "ABSENT"
            result?.timestamp shouldBe 2000L
        }
    }
}
