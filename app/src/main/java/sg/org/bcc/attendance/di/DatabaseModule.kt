package sg.org.bcc.attendance.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import sg.org.bcc.attendance.data.local.AttendanceDatabase
import sg.org.bcc.attendance.data.local.dao.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AttendanceDatabase {
        return Room.databaseBuilder(
            context,
            AttendanceDatabase::class.java,
            "attendance.db"
        ).fallbackToDestructiveMigration() // For development phase
        .build()
    }

    @Provides
    fun provideAttendeeDao(db: AttendanceDatabase): AttendeeDao = db.attendeeDao()

    @Provides
    fun provideAttendanceDao(db: AttendanceDatabase): AttendanceDao = db.attendanceDao()

    @Provides
    fun providePersistentQueueDao(db: AttendanceDatabase): PersistentQueueDao = db.persistentQueueDao()

    @Provides
    fun provideSyncJobDao(db: AttendanceDatabase): SyncJobDao = db.syncJobDao()

    @Provides
    fun provideQueueArchiveDao(db: AttendanceDatabase): QueueArchiveDao = db.queueArchiveDao()

    @Provides
    fun provideEventDao(db: AttendanceDatabase): EventDao = db.eventDao()

    @Provides
    fun provideGroupDao(db: AttendanceDatabase): GroupDao = db.groupDao()

    @Provides
    fun provideAttendeeGroupMappingDao(db: AttendanceDatabase): AttendeeGroupMappingDao = db.attendeeGroupMappingDao()

    @Provides
    fun provideSyncLogDao(db: AttendanceDatabase): SyncLogDao = db.syncLogDao()
}
