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
import sg.org.bcc.attendance.data.local.dao.AttendeeDao
import sg.org.bcc.attendance.data.local.entities.Attendee
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class AttendeeSearchTest {
    private lateinit var db: AttendanceDatabase
    private lateinit var dao: AttendeeDao

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            dao = db.attendeeDao()
            
            val attendees = listOf(
                Attendee("1", "Matthew Chng", "Matt"),
                Attendee("2", "John Doe", "Johnny"),
                Attendee("3", "Jane Smith", "Jane")
            )
            dao.insertAll(attendees)
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `searching for exact short name should return result`() {
        runBlocking {
            val results = dao.searchAttendees("Matt")
            results.size shouldBe 1
            results.first().fullName shouldBe "Matthew Chng"
        }
    }

    @Test
    fun `searching for exact full name should return result`() {
        runBlocking {
            val results = dao.searchAttendees("Jane Smith")
            results.size shouldBe 1
            results.first().id shouldBe "3"
        }
    }

    @Test
    fun `searching for non-existent name should return empty`() {
        runBlocking {
            val results = dao.searchAttendees("Xavier")
            results.isEmpty() shouldBe true
        }
    }
}
