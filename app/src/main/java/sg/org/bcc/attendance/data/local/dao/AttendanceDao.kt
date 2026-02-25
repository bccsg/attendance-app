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
    @Query("SELECT * FROM attendance_records WHERE eventId = :eventId")
    fun getAttendanceFlow(eventId: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE eventId = :eventId")
    suspend fun getAttendanceForEvent(eventId: String): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE eventId = :eventId AND attendeeId = :attendeeId")
    suspend fun getRecord(eventId: String, attendeeId: String): AttendanceRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AttendanceRecord)

    @Transaction
    suspend fun upsertIfNewer(record: AttendanceRecord) {
        val existing = getRecord(record.eventId, record.attendeeId)
        if (existing == null || record.timestamp > existing.timestamp) {
            insert(record)
        }
    }

    @Transaction
    suspend fun upsertAllIfNewer(records: List<AttendanceRecord>) {
        if (records.isEmpty()) return
        
        // Batch Reduction: Latest per attendee (max timestamp)
        val reduced = records.groupBy { it.attendeeId }
            .mapValues { (_, group) -> group.maxBy { it.timestamp } }
            .values
            .toList()

        reduced.forEach { upsertIfNewer(it) }
    }

    @Query("SELECT EXISTS(SELECT 1 FROM attendance_records WHERE attendeeId = :attendeeId)")
    suspend fun hasAttendanceForAttendee(attendeeId: String): Boolean

    @Query("DELETE FROM attendance_records")
    suspend fun clearAll()

    @Query("DELETE FROM attendance_records WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: String)
}
