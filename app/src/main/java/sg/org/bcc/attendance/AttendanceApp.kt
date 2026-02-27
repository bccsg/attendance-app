package sg.org.bcc.attendance

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import sg.org.bcc.attendance.sync.SyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class AttendanceApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // App enters foreground
                syncScheduler.scheduleSync()
                syncScheduler.schedulePeriodicPull()
            }

            override fun onStop(owner: LifecycleOwner) {
                // App enters background
                syncScheduler.cancelAllWork()
            }
        })
    }
}
