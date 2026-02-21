package sg.org.bcc.attendance.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import sg.org.bcc.attendance.util.time.TimeProvider
import sg.org.bcc.attendance.util.time.TrueTimeProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: TrueTimeProvider): TimeProvider
}
