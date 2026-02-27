package sg.org.bcc.attendance.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import sg.org.bcc.attendance.sync.DefaultForegroundStateProvider
import sg.org.bcc.attendance.sync.ForegroundStateProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindForegroundStateProvider(
        impl: DefaultForegroundStateProvider
    ): ForegroundStateProvider
}
