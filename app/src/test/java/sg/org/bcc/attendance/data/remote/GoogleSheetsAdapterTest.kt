package sg.org.bcc.attendance.data.remote

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sg.org.bcc.attendance.data.local.entities.Event
import sg.org.bcc.attendance.sync.NoOpSyncLogScope

@RunWith(RobolectricTestRunner::class)
class GoogleSheetsAdapterTest {

    private lateinit var authManager: AuthManager
    private lateinit var mockService: Sheets
    private lateinit var adapter: GoogleSheetsAdapter

    @Before
    fun setup() {
        authManager = mockk(relaxed = true)
        mockService = mockk(relaxed = true)
        adapter = spyk(GoogleSheetsAdapter(authManager))
        every { adapter.getSheetsService() } returns mockService
    }

    @Test
    fun testFormulaExtraction() {
        runBlocking {
            val event = Event(id = "E1", title = "260225 1000 Test", date = "2026-02-25", time = "1000")
            
            val formula = "=IFERROR(VLOOKUP(INDIRECT(\"A\"&ROW()), IMPORTRANGE(\"master-id\", \"Attendees!A:B\"), 2, FALSE), \"John Doe (Not Found)\")"
            val response = ValueRange().setValues(listOf(
                listOf("A1", formula, "PRESENT", "TRUE", 46078.4166666667)
            ))

            val getRequest = mockk<Sheets.Spreadsheets.Values.Get>(relaxed = true)
            every { mockService.spreadsheets().values().get(any(), any()) } returns getRequest
            every { getRequest.setValueRenderOption(any()) } returns getRequest
            every { getRequest.execute() } returns response

            val result = adapter.fetchAttendanceForEvent(event, 0, NoOpSyncLogScope)

            result.records.size shouldBe 1
            result.records[0].attendeeId shouldBe "A1"
            result.records[0].fullName shouldBe "John Doe"
        }
    }

    @Test
    fun testLegacyNameHandling() {
        runBlocking {
            val event = Event(id = "E1", title = "260225 1000 Test", date = "2026-02-25", time = "1000")
            
            val response = ValueRange().setValues(listOf(
                listOf("A1", "Jane Smith (Not Found)", "PRESENT", "TRUE", 46078.4166666667)
            ))

            val getRequest = mockk<Sheets.Spreadsheets.Values.Get>(relaxed = true)
            every { mockService.spreadsheets().values().get(any(), any()) } returns getRequest
            every { getRequest.setValueRenderOption(any()) } returns getRequest
            every { getRequest.execute() } returns response

            val result = adapter.fetchAttendanceForEvent(event, 0, NoOpSyncLogScope)

            result.records.size shouldBe 1
            result.records[0].fullName shouldBe "Jane Smith"
        }
    }
    
    @Test
    fun testQuoteUnescaping() {
        runBlocking {
            val event = Event(id = "E1", title = "260225 1000 Test", date = "2026-02-25", time = "1000")
            
            val formula = "=IFERROR(VLOOKUP(...), \"O''Brian \"\"The Great\"\" (Not Found)\")"
            val response = ValueRange().setValues(listOf(
                listOf("A1", formula, "PRESENT", "TRUE", 46078.4166666667)
            ))

            val getRequest = mockk<Sheets.Spreadsheets.Values.Get>(relaxed = true)
            every { mockService.spreadsheets().values().get(any(), any()) } returns getRequest
            every { getRequest.setValueRenderOption(any()) } returns getRequest
            every { getRequest.execute() } returns response

            val result = adapter.fetchAttendanceForEvent(event, 0, NoOpSyncLogScope)

            result.records.size shouldBe 1
            result.records[0].fullName shouldBe "O''Brian \"The Great\""
        }
    }
}
