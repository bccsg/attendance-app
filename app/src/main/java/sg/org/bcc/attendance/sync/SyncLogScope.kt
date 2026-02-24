package sg.org.bcc.attendance.sync

import sg.org.bcc.attendance.data.local.dao.SyncLogDao
import sg.org.bcc.attendance.data.local.entities.SyncLog
import java.util.UUID

interface SyncLogScope {
    suspend fun log(
        operation: String,
        success: Boolean,
        params: String? = null,
        error: String? = null,
        stackTrace: String? = null
    )
    
    val triggerId: String
    val triggerType: String
}

class DatabaseSyncLogScope(
    private val dao: SyncLogDao,
    override val triggerType: String,
    override val triggerId: String = UUID.randomUUID().toString()
) : SyncLogScope {
    
    override suspend fun log(
        operation: String,
        success: Boolean,
        params: String?,
        error: String?,
        stackTrace: String?
    ) {
        dao.insert(
            SyncLog(
                triggerId = triggerId,
                triggerType = triggerType,
                operation = operation,
                params = params,
                status = if (success) "SUCCESS" else "FAILED",
                errorMessage = error,
                stackTrace = stackTrace
            )
        )
    }
}

object NoOpSyncLogScope : SyncLogScope {
    override suspend fun log(operation: String, success: Boolean, params: String?, error: String?, stackTrace: String?) {}
    override val triggerId: String = ""
    override val triggerType: String = "NONE"
}
