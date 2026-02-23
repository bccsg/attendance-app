package sg.org.bcc.attendance.data.remote

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sg.org.bcc.attendance.data.local.entities.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSheetsAdapter @Inject constructor(
    private val authManager: AuthManager
) : AttendanceCloudProvider {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

    private val masterSpreadsheetId = sg.org.bcc.attendance.BuildConfig.MASTER_SHEET_ID
    private val eventSpreadsheetId = sg.org.bcc.attendance.BuildConfig.EVENT_SHEET_ID

    private fun getSheetsService(): Sheets {
        val accessToken = authManager.getAccessToken() ?: throw IllegalStateException("Not authenticated")
        val credentials = GoogleCredentials.create(AccessToken(accessToken, null))
        
        return Sheets.Builder(httpTransport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName("BCC Attendance")
            .build()
    }

    override suspend fun pushAttendance(event: Event, records: List<AttendanceRecord>): PushResult = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        val sheetTitle = event.title // Format: yyMMdd HHmm Name
        
        // 1. Ensure worksheet exists
        ensureWorksheetExists(service, eventSpreadsheetId, sheetTitle)

        // 2. Prepare data for push (Batch update or Append)
        // For simplicity, we'll implement a basic append/update logic here.
        // In a real app, we'd map Attendee ID to Row/Column.
        
        val values = records.map { listOf(it.attendeeId, it.state, it.timestamp.toString()) }
        val body = ValueRange().setValues(values)
        
        try {
            service.spreadsheets().values()
                .append(eventSpreadsheetId, "'$sheetTitle'!A1", body)
                .setValueInputOption("RAW")
                .execute()
            PushResult.Success
        } catch (e: Exception) {
            PushResult.Error(e.message ?: "Sync failed", isRetryable = true)
        }
    }

    private suspend fun ensureWorksheetExists(service: Sheets, spreadsheetId: String, title: String) = withContext(Dispatchers.IO) {
        val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
        val sheetExists = spreadsheet.sheets.any { it.properties.title == title }
        
        if (!sheetExists) {
            val addSheetRequest = com.google.api.services.sheets.v4.model.Request().setAddSheet(
                com.google.api.services.sheets.v4.model.AddSheetRequest().setProperties(
                    com.google.api.services.sheets.v4.model.SheetProperties().setTitle(title)
                )
            )
            val batchUpdate = com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
                .setRequests(listOf(addSheetRequest))
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()
        }
    }

    override suspend fun fetchMasterAttendees(): List<Attendee> = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        android.util.Log.d("AttendanceSync", "Fetching attendees from sheet: $masterSpreadsheetId")
        try {
            val response = service.spreadsheets().values()
                .get(masterSpreadsheetId, "Attendees!A2:E")
                .execute()
            
            val values = response.getValues()
            android.util.Log.d("AttendanceSync", "Raw attendee rows: ${values?.size ?: 0}")
            
            values?.filter { it.size >= 2 }?.map { row ->
                Attendee(
                    id = row[0].toString(),
                    fullName = row[1].toString(),
                    shortName = row.getOrNull(2)?.toString()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            val type = e.javaClass.simpleName
            android.util.Log.e("AttendanceSync", "Error fetching attendees ($type): ${e.message}", e)
            throw Exception("[$type] ${e.message ?: "No message"}")
        }
    }

    override suspend fun fetchMasterGroups(): List<Group> = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        android.util.Log.d("AttendanceSync", "Fetching groups...")
        try {
            val response = service.spreadsheets().values()
                .get(masterSpreadsheetId, "Groups!A2:B")
                .execute()
            
            val values = response.getValues()
            android.util.Log.d("AttendanceSync", "Raw group rows: ${values?.size ?: 0}")
            
            values?.filter { it.size >= 2 }?.map { row ->
                Group(
                    groupId = row[0].toString(),
                    name = row[1].toString()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            val type = e.javaClass.simpleName
            android.util.Log.e("AttendanceSync", "Error fetching groups ($type): ${e.message}")
            throw Exception("[$type] ${e.message ?: "No message"}")
        }
    }

    override suspend fun fetchAttendeeGroupMappings(): List<AttendeeGroupMapping> = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        android.util.Log.d("AttendanceSync", "Fetching mappings...")
        try {
            val response = service.spreadsheets().values()
                .get(masterSpreadsheetId, "Mappings!A2:B")
                .execute()
            
            val values = response.getValues()
            android.util.Log.d("AttendanceSync", "Raw mapping rows: ${values?.size ?: 0}")
            
            values?.filter { it.size >= 2 }?.map { row ->
                AttendeeGroupMapping(
                    attendeeId = row[0].toString(),
                    groupId = row[1].toString()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            val type = e.javaClass.simpleName
            android.util.Log.e("AttendanceSync", "Error fetching mappings ($type): ${e.message}")
            throw Exception("[$type] ${e.message ?: "No message"}")
        }
    }

    override suspend fun fetchAttendanceForEvent(event: Event): List<AttendanceRecord> = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        val response = try {
            service.spreadsheets().values()
                .get(eventSpreadsheetId, "'${event.title}'!A1:C")
                .execute()
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        
        response.getValues()?.map { row ->
            AttendanceRecord(
                eventId = event.id,
                attendeeId = row[0].toString(),
                state = row[1].toString(),
                timestamp = row[2].toString().toLongOrNull() ?: 0L
            )
        } ?: emptyList()
    }

    override suspend fun fetchRecentEvents(days: Int): List<Event> = withContext(Dispatchers.IO) {
        val service = getSheetsService()
        val spreadsheet = service.spreadsheets().get(eventSpreadsheetId).execute()
        
        // In GSheets, each worksheet IS an event
        spreadsheet.sheets.map { sheet ->
            Event(
                id = UUID.randomUUID().toString(),
                title = sheet.properties.title,
                cloudEventId = sheet.properties.sheetId.toString()
            )
        }
    }
}
