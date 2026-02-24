package sg.org.bcc.attendance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import sg.org.bcc.attendance.data.local.dao.*
import sg.org.bcc.attendance.data.local.entities.*

@Database(
    entities = [
        Attendee::class,
        AttendeeFts::class,
        Group::class,
        AttendeeGroupMapping::class,
        Event::class,
        AttendanceRecord::class,
        PersistentQueue::class,
        SyncJob::class,
        QueueArchive::class,
        SyncLog::class
    ],
    version = 11,
    exportSchema = true
)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun attendeeDao(): AttendeeDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun persistentQueueDao(): PersistentQueueDao
    abstract fun syncJobDao(): SyncJobDao
    abstract fun queueArchiveDao(): QueueArchiveDao
    abstract fun eventDao(): EventDao
    abstract fun groupDao(): GroupDao
    abstract fun attendeeGroupMappingDao(): AttendeeGroupMappingDao
    abstract fun syncLogDao(): SyncLogDao
}
