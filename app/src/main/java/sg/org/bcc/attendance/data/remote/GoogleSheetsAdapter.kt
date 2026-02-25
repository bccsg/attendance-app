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
import sg.org.bcc.attendance.sync.SyncLogScope
import sg.org.bcc.attendance.util.EventSuggester
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

    private suspend fun <T> runWithLogging(
        scope: SyncLogScope,
        operation: String,
        params: String? = null,
        block: suspend () -> T
    ): T {
        return try {
            val result = block()
            scope.log(operation, true, params = params)
            result
        } catch (e: Exception) {
            scope.log(operation, false, params = params, error = e.message, stackTrace = e.stackTraceToString())
            throw e
        }
    }

    internal fun getSheetsService(): Sheets {
        val accessToken = authManager.getAccessToken() ?: throw IllegalStateException("Not authenticated")
        val credentials = GoogleCredentials.create(AccessToken(accessToken, null))
        
        return Sheets.Builder(httpTransport, jsonFactory, HttpCredentialsAdapter(credentials))
            .setApplicationName("BCC Attendance")
            .build()
    }

    private suspend fun ensureAuthenticated() {
        if (authManager.isTokenExpired()) {
            val success = authManager.silentRefresh()
            if (!success) {
                throw IllegalStateException("Authentication expired and refresh failed")
            }
        }
    }

    override suspend fun pushAttendance(
        event: Event, 
        records: List<AttendanceRecord>,
        scope: SyncLogScope,
        failIfMissing: Boolean
    ): PushResult = runWithLogging(scope, "pushAttendance", "event='${event.title}', records=${records.size}, failIfMissing=$failIfMissing") {
        withContext(Dispatchers.IO) {
            try {
                ensureAuthenticated()
                val service = getSheetsService()
                val sheetTitle = event.title // Format: yyMMdd HHmm Name
                
                // 1. Ensure worksheet exists and get its ID
                val sheetId = if (failIfMissing) {
                    getWorksheetId(service, eventSpreadsheetId, sheetTitle) 
                        ?: return@withContext PushResult.Error("Cloud worksheet not found for '${event.title}'", isRetryable = false)
                } else {
                    ensureWorksheetExists(service, eventSpreadsheetId, sheetTitle)
                }
                val cloudIdStr = sheetId.toString()

                // 2. Prepare data for push if any records exist
                var lastRowIndex = event.lastProcessedRowIndex
                if (records.isNotEmpty()) {
                    val userEmail = authManager.getEmail() ?: "unknown"
                    val sgtOffsetMs = 8 * 3600 * 1000L
                    val finalFormula = "=COUNTIF(INDIRECT(\"A\"&ROW()&\":A\"), INDIRECT(\"A\"&ROW())) = 1"
                    
                    val values = records.map { record ->
                        // Convert Unix ms to Google Sheets Serial Number in SGT (+8h)
                        val sgtTimestamp = record.timestamp + sgtOffsetMs
                        val serialNumber = (sgtTimestamp / 86400000.0) + 25569.0
                        
                        // Fallback name if VLOOKUP fails (includes a suffix)
                        val escapedLocalName = record.fullName.replace("\"", "\"\"")
                        val vlookupFormula = "=IFERROR(VLOOKUP(INDIRECT(\"A\"&ROW()), IMPORTRANGE(\"$masterSpreadsheetId\", \"Attendees!A:B\"), 2, FALSE), \"$escapedLocalName (Not Found)\")"
                        
                        // Order: ID (A), Name (B), State (C), Final (D), Time (E), User (F)
                        listOf(record.attendeeId, vlookupFormula, record.state, finalFormula, serialNumber, userEmail) 
                    }
                    val body = ValueRange().setValues(values)
                    
                    val appendResponse = service.spreadsheets().values()
                        .append(eventSpreadsheetId, "'$sheetTitle'!A1:F1", body)
                        .setValueInputOption("USER_ENTERED")
                        .execute()
                    
                    // 3. Format the appended rows Column D as Checkbox
                    // We extract the range from the appendResponse to target exactly the new rows
                    val updatedRange = appendResponse.updates.updatedRange // e.g. "Sheet1!A2:F5"
                    val rowRangeRegex = Regex("!A(\\d+):F(\\d+)")
                    val match = rowRangeRegex.find(updatedRange)
                    if (match != null) {
                        val startRow = match.groupValues[1].toInt() - 1 // 0-indexed
                        val endRow = match.groupValues[2].toInt()
                        lastRowIndex = endRow - 1 // Data rows count (header excluded)
                        
                        val checkboxRequest = com.google.api.services.sheets.v4.model.Request().setRepeatCell(
                            com.google.api.services.sheets.v4.model.RepeatCellRequest()
                                .setRange(com.google.api.services.sheets.v4.model.GridRange()
                                    .setSheetId(sheetId)
                                    .setStartRowIndex(startRow)
                                    .setEndRowIndex(endRow)
                                    .setStartColumnIndex(3)
                                    .setEndColumnIndex(4)
                                )
                                .setCell(com.google.api.services.sheets.v4.model.CellData()
                                    .setDataValidation(com.google.api.services.sheets.v4.model.DataValidationRule()
                                        .setCondition(com.google.api.services.sheets.v4.model.BooleanCondition()
                                            .setType("BOOLEAN")
                                        )
                                    )
                                )
                                .setFields("dataValidation")
                        )
                        
                        val batchUpdate = com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
                            .setRequests(listOf(checkboxRequest))
                        service.spreadsheets().batchUpdate(eventSpreadsheetId, batchUpdate).execute()
                    }
                } else {
                    // Even if no records pushed, we might want to know the current cloud end
                    // But for pushAttendance, if records is empty, we just return current
                    val spreadsheet = service.spreadsheets().get(eventSpreadsheetId).execute()
                    val sheet = spreadsheet.sheets.find { it.properties.sheetId == sheetId }
                    lastRowIndex = (sheet?.properties?.gridProperties?.rowCount ?: 1) - 1
                }
                
                // 4. Return mapping if local event doesn't have it yet
                if (event.cloudEventId != cloudIdStr) {
                    PushResult.SuccessWithMapping(cloudIdStr, lastRowIndex)
                } else {
                    PushResult.Success(lastRowIndex)
                }
            } catch (e: Exception) {
                PushResult.Error(e.message ?: "Sync failed", isRetryable = true)
            }
        }
    }

    private suspend fun getWorksheetId(service: Sheets, spreadsheetId: String, title: String): Int? = withContext(Dispatchers.IO) {
        val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
        spreadsheet.sheets.find { it.properties.title == title }?.properties?.sheetId
    }

    private suspend fun ensureWorksheetExists(service: Sheets, spreadsheetId: String, title: String): Int = withContext(Dispatchers.IO) {
        val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
        val existingSheet = spreadsheet.sheets.find { it.properties.title == title }
        
        if (existingSheet != null) {
            return@withContext existingSheet.properties.sheetId
        }
        
        // Find target index for descending order (newest first)
        val existingSheets = spreadsheet.sheets
        var targetIndex = -1
        
        // 1. Try to find chronological spot among existing event sheets
        for (i in existingSheets.indices) {
            val et = existingSheets[i].properties.title
            // Event sheets start with 6 digits (yyMMdd)
            if (et.length >= 6 && et.take(6).all { it.isDigit() }) {
                if (et <= title) {
                    targetIndex = i
                    break
                }
            }
        }
        
        // 2. Fallback if no spot found (no events, or this is the oldest event)
        if (targetIndex == -1) {
            val mappingsIndex = existingSheets.indexOfFirst { it.properties.title == "Mappings" }
            val lastEventIndex = existingSheets.indexOfLast { 
                val et = it.properties.title
                et.length >= 6 && et.take(6).all { it.isDigit() } 
            }
            
            targetIndex = when {
                lastEventIndex != -1 -> lastEventIndex + 1
                mappingsIndex != -1 -> mappingsIndex + 1
                else -> existingSheets.size
            }
        }

        val newSheetId = Random().nextInt(Int.MAX_VALUE)

        // 1. Create the sheet
        val addSheetRequest = com.google.api.services.sheets.v4.model.Request().setAddSheet(
            com.google.api.services.sheets.v4.model.AddSheetRequest().setProperties(
                com.google.api.services.sheets.v4.model.SheetProperties()
                    .setTitle(title)
                    .setSheetId(newSheetId)
                    .setIndex(targetIndex)
                    .setGridProperties(com.google.api.services.sheets.v4.model.GridProperties().setFrozenRowCount(1))
            )
        )
        
        // 2. Bold the header row (Row 1)
        val boldHeaderRequest = com.google.api.services.sheets.v4.model.Request().setRepeatCell(
            com.google.api.services.sheets.v4.model.RepeatCellRequest()
                .setRange(com.google.api.services.sheets.v4.model.GridRange()
                    .setSheetId(newSheetId)
                    .setStartRowIndex(0)
                    .setEndRowIndex(1)
                )
                .setCell(com.google.api.services.sheets.v4.model.CellData()
                    .setUserEnteredFormat(com.google.api.services.sheets.v4.model.CellFormat()
                        .setTextFormat(com.google.api.services.sheets.v4.model.TextFormat().setBold(true))
                    )
                )
                .setFields("userEnteredFormat.textFormat.bold")
        )

        // 3. Format Column E (index 4) as Date Time from Row 2 down
        val timeFormatRequest = com.google.api.services.sheets.v4.model.Request().setRepeatCell(
            com.google.api.services.sheets.v4.model.RepeatCellRequest()
                .setRange(com.google.api.services.sheets.v4.model.GridRange()
                    .setSheetId(newSheetId)
                    .setStartRowIndex(1)
                    .setStartColumnIndex(4)
                    .setEndColumnIndex(5)
                )
                .setCell(com.google.api.services.sheets.v4.model.CellData()
                    .setUserEnteredFormat(com.google.api.services.sheets.v4.model.CellFormat()
                        .setNumberFormat(com.google.api.services.sheets.v4.model.NumberFormat()
                            .setType("DATE_TIME")
                            .setPattern("yyyy-mm-dd hh:mm:ss")
                        )
                    )
                )
                .setFields("userEnteredFormat.numberFormat")
        )

        fun createWidthRequest(start: Int, end: Int, size: Int) = com.google.api.services.sheets.v4.model.Request().setUpdateDimensionProperties(
            com.google.api.services.sheets.v4.model.UpdateDimensionPropertiesRequest()
                .setRange(com.google.api.services.sheets.v4.model.DimensionRange()
                    .setSheetId(newSheetId)
                    .setDimension("COLUMNS")
                    .setStartIndex(start)
                    .setEndIndex(end)
                )
                .setProperties(com.google.api.services.sheets.v4.model.DimensionProperties().setPixelSize(size))
                .setFields("pixelSize")
        )

        // 4. Add Filter View: "Final Present Only"
        val filterViewRequest = com.google.api.services.sheets.v4.model.Request().setAddFilterView(
            com.google.api.services.sheets.v4.model.AddFilterViewRequest().setFilter(
                com.google.api.services.sheets.v4.model.FilterView()
                    .setTitle("Final Present Only")
                    .setRange(com.google.api.services.sheets.v4.model.GridRange().setSheetId(newSheetId))
                    .setCriteria(mapOf(
                        "2" to com.google.api.services.sheets.v4.model.FilterCriteria()
                            .setCondition(com.google.api.services.sheets.v4.model.BooleanCondition()
                                .setType("TEXT_EQ")
                                .setValues(listOf(com.google.api.services.sheets.v4.model.ConditionValue().setUserEnteredValue("PRESENT")))
                            ),
                        "3" to com.google.api.services.sheets.v4.model.FilterCriteria()
                            .setCondition(com.google.api.services.sheets.v4.model.BooleanCondition()
                                .setType("TEXT_EQ")
                                .setValues(listOf(com.google.api.services.sheets.v4.model.ConditionValue().setUserEnteredValue("TRUE")))
                            )
                    ))
            )
        )

        val batchUpdate = com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
            .setRequests(listOf(
                addSheetRequest, 
                boldHeaderRequest, 
                timeFormatRequest, 
                filterViewRequest,
                createWidthRequest(1, 2, 200), // B
                createWidthRequest(4, 6, 200)  // E, F
            ))
        
        service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()

        // 5. Update headers (ID, Name, State, Final, Pushed At, Pushed By)
        val headerValues = listOf(listOf("Attendee ID", "Full Name", "State", "Final", "Pushed At", "Pushed By"))
        val headerBody = ValueRange().setValues(headerValues)
        service.spreadsheets().values()
            .update(spreadsheetId, "'$title'!A1:F1", headerBody)
            .setValueInputOption("RAW")
            .execute()
        
        newSheetId
    }

    override suspend fun fetchMasterAttendees(scope: SyncLogScope): List<Attendee> = runWithLogging(scope, "fetchMasterAttendees") {
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
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
    }

    override suspend fun fetchMasterGroups(scope: SyncLogScope): List<Group> = runWithLogging(scope, "fetchMasterGroups") {
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
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
                val msg = e.message ?: "No message"
                if (msg.contains("Unable to parse range") || msg.contains("Grid with id")) {
                    android.util.Log.e("AttendanceSync", "CRITICAL: 'Groups' worksheet not found in master sheet!")
                }
                android.util.Log.e("AttendanceSync", "Error fetching groups ($type): $msg")
                throw Exception("[$type] $msg")
            }
        }
    }

    override suspend fun fetchAttendeeGroupMappings(scope: SyncLogScope): List<AttendeeGroupMapping> = runWithLogging(scope, "fetchAttendeeGroupMappings") {
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val service = getSheetsService()
            android.util.Log.d("AttendanceSync", "Fetching mappings...")
            
            // DEBUG: List all sheets to verify names
            try {
                val spreadsheet = service.spreadsheets().get(masterSpreadsheetId).execute()
                val sheetNames = spreadsheet.sheets.map { it.properties.title }
                android.util.Log.d("AttendanceSync", "Available sheets: $sheetNames")
            } catch (e: Exception) {
                android.util.Log.e("AttendanceSync", "Failed to list sheets: ${e.message}")
            }

            try {
                val response = service.spreadsheets().values()
                    .get(masterSpreadsheetId, "Mappings!A2:B")
                    .execute()
                
                val values = response.getValues()
                android.util.Log.d("AttendanceSync", "Raw mapping rows: ${values?.size ?: 0}")
                
                // User sheet format: Group ID, Attendee ID
                values?.filter { it.size >= 2 }?.map { row ->
                    AttendeeGroupMapping(
                        groupId = row[0].toString(),    // Column A is Group ID
                        attendeeId = row[1].toString()  // Column B is Attendee ID
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                val type = e.javaClass.simpleName
                val msg = e.message ?: "No message"
                if (msg.contains("Unable to parse range") || msg.contains("Grid with id")) {
                    android.util.Log.e("AttendanceSync", "CRITICAL: 'Mappings' worksheet not found in master sheet!")
                }
                android.util.Log.e("AttendanceSync", "Error fetching mappings ($type): $msg")
                throw Exception("[$type] $msg")
            }
        }
    }

    override suspend fun fetchMasterListVersion(scope: SyncLogScope): String = runWithLogging(scope, "fetchMasterListVersion") {
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val service = getSheetsService()
            // We only need the ETag, so we fetch minimal fields
            val spreadsheet = service.spreadsheets().get(masterSpreadsheetId)
                .setFields("spreadsheetId")
                .execute()
            
            spreadsheet.get("etag")?.toString() ?: System.currentTimeMillis().toString()
        }
    }

    override suspend fun fetchAttendanceForEvent(
        event: Event,
        startIndex: Int,
        scope: SyncLogScope
    ): PullResult = runWithLogging(scope, "fetchAttendanceForEvent", "event='${event.title}', start=$startIndex") {
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val service = getSheetsService()
            // Range A{startIndex + 2}:F skips header (1) and M rows
            val range = "'${event.title}'!A${startIndex + 2}:F"
            val response = try {
                service.spreadsheets().values()
                    .get(eventSpreadsheetId, range) 
                    .setValueRenderOption("FORMULA")
                    .execute()
            } catch (e: Exception) {
                return@withContext PullResult(emptyList(), startIndex)
            }
            
            val values = response.getValues()
            val sgtOffsetMs = 8 * 3600 * 1000L

            val records = values?.mapNotNull { row ->
                if (row.size < 5) return@mapNotNull null
                
                val attendeeId = row[0].toString()
                
                // Column B (index 1) is Name. In our pushes, it's a formula: 
                // =IFERROR(VLOOKUP(...), "Local Name (Not Found)")
                val rawName = row[1].toString()
                val parsedFullName = if (rawName.startsWith("=")) {
                    // It's a formula. Extract fallback name if possible.
                    // The formula is: =IFERROR(VLOOKUP(...), "Name (Not Found)")
                    // We need to unescape double quotes in the extracted name
                    val fallbackRegex = Regex("IFERROR\\(VLOOKUP\\(.*\\), \"(.*?) \\(Not Found\\)\"\\)")
                    val match = fallbackRegex.find(rawName)
                    match?.groupValues?.get(1)?.replace("\"\"", "\"") ?: attendeeId
                } else {
                    // Fallback for legacy plain-text names or non-formula entries
                    rawName.removeSuffix(" (Not Found)")
                }

                val state = row[2].toString()
                // index 3 is Final, skip
                val rawValue = row[4] // Timestamp is now Column E
                
                val timestamp = when (rawValue) {
                    is Number -> {
                        // Convert Serial Number back to SGT ms, then shift to UTC ms
                        val sgtMs = ((rawValue.toDouble() - 25569.0) * 86400000.0).toLong()
                        sgtMs - sgtOffsetMs
                    }
                    else -> {
                        // Fallback if it's a string
                        rawValue.toString().toLongOrNull() ?: 0L
                    }
                }

                AttendanceRecord(
                    eventId = event.id,
                    attendeeId = attendeeId,
                    fullName = parsedFullName,
                    state = state,
                    timestamp = timestamp
                )
            } ?: emptyList()

            // Calculate lastRowIndex. 
            // startIndex + records.size is the new lastRowIndex.
            PullResult(records, startIndex + (values?.size ?: 0))
        }
    }

    override suspend fun fetchRecentEvents(days: Int, scope: SyncLogScope): List<Event> = runWithLogging(scope, "fetchRecentEvents", "days=$days") {
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val service = getSheetsService()
            val spreadsheet = service.spreadsheets().get(eventSpreadsheetId).execute()
            
            // In GSheets, each worksheet IS an event
            spreadsheet.sheets.mapNotNull { sheet ->
                val title = sheet.properties.title
                val date = EventSuggester.parseDate(title)?.toString() ?: return@mapNotNull null
                val time = title.split(" ").getOrNull(1) ?: return@mapNotNull null
                
                Event(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    date = date,
                    time = time,
                    cloudEventId = sheet.properties.sheetId.toString()
                )
            }
        }
    }
}
