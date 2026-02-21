package sg.org.bcc.attendance.util.time

import com.instacart.library.truetime.TrueTime
import javax.inject.Inject
import javax.inject.Singleton

interface TimeProvider {
    fun now(): Long
}

@Singleton
class TrueTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Long {
        return if (TrueTime.isInitialized()) {
            TrueTime.now().time
        } else {
            System.currentTimeMillis()
        }
    }
}
