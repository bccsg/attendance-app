package sg.org.bcc.attendance.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import sg.org.bcc.attendance.data.remote.AuthData
import sg.org.bcc.attendance.data.remote.AuthDataSerializer
import sg.org.bcc.attendance.util.SecurityManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthDataStore(
        @ApplicationContext context: Context,
        authDataSerializer: AuthDataSerializer
    ): DataStore<AuthData> {
        return DataStoreFactory.create(
            serializer = authDataSerializer,
            produceFile = { context.dataStoreFile("auth_data.json") }
        )
    }
}
