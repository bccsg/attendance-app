package sg.org.bcc.attendance.sync

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

interface ForegroundStateProvider {
    fun isForeground(): Boolean
}

@Singleton
class DefaultForegroundStateProvider @Inject constructor() : ForegroundStateProvider {
    override fun isForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
}
