package sg.org.bcc.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import sg.org.bcc.attendance.data.local.entities.AttendanceRecord

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE eventTitle = :eventTitle")
    fun getAttendanceFlow(eventTitle: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE eventTitle = :eventTitle")
    suspend fun getAttendanceForEvent(eventTitle: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE eventTitle = :eventTitle AND attendeeId = :attendeeId")
    suspend fun getRecord(eventTitle: String, attendeeId: String): AttendanceRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AttendanceRecord)

    @Transaction
    suspend fun upsertIfNewer(record: AttendanceRecord) {
        val existing = getRecord(record.eventTitle, record.attendeeId)
        if (existing == null || record.timestamp > existing.timestamp) {
            insert(record)
        }
    }

    @Transaction
    suspend fun upsertAllIfNewer(records: List<AttendanceRecord>) {
        records.forEach { upsertIfNewer(it) }
    }

    @Query("DELETE FROM attendance_records")
    suspend fun clearAll()
}
