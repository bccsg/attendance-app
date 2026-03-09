package sg.org.bcc.attendance.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import sg.org.bcc.attendance.util.time.TimeProvider
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthState {
    AUTHENTICATED,
    EXPIRED,
    UNAUTHENTICATED
}

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
    private val dataStore: DataStore<AuthData>
) {
    companion object {
        const val REQUIRED_DOMAIN = "bethany.sg"
        
        private val clientSecrets by lazy {
            GoogleClientSecrets.load(
                GsonFactory.getDefaultInstance(),
                StringReader(sg.org.bcc.attendance.BuildConfig.GOOGLE_CLIENT_SECRETS_JSON)
            )
        }
        
        private val details get() = clientSecrets.details
        private val CLIENT_ID get() = details.clientId
        private val CLIENT_SECRET get() = details.clientSecret
        
        private const val REDIRECT_URI = "sg.org.bcc.attendance:/oauth2redirect"
        private const val SCOPES = "https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/userinfo.email"
    }

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val transport = NetHttpTransport()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val authState: StateFlow<AuthState> = dataStore.data
        .map { data ->
            when {
                data.accessToken == null -> AuthState.UNAUTHENTICATED
                data.accessToken == "demo_token" -> AuthState.AUTHENTICATED
                isTokenExpired(data) -> AuthState.EXPIRED
                else -> AuthState.AUTHENTICATED
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AuthState.UNAUTHENTICATED)

    val isAuthed: StateFlow<Boolean> = dataStore.data
        .map { it.email != null && it.refreshToken != null }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val isDemoMode: StateFlow<Boolean> = dataStore.data
        .map { it.accessToken == "demo_token" }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val emailFlow: StateFlow<String?> = dataStore.data
        .map { it.email }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun getAuthUrl(): String {
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$CLIENT_ID&" +
                "redirect_uri=$REDIRECT_URI&" +
                "response_type=code&" +
                "scope=$SCOPES&" +
                "access_type=offline&" +
                "prompt=consent&" +
                "hd=$REQUIRED_DOMAIN"
    }

    suspend fun exchangeCodeForTokens(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = GoogleAuthorizationCodeTokenRequest(
                transport,
                jsonFactory,
                "https://oauth2.googleapis.com/token",
                CLIENT_ID,
                CLIENT_SECRET,
                code,
                REDIRECT_URI
            ).execute()

            val idToken = response.parseIdToken()
            val email = idToken.payload.email
            
            if (!isEmailValid(email)) {
                return@withContext false
            }

            saveTokens(
                email = email,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiryTime = timeProvider.now() + (response.expiresInSeconds * 1000)
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun saveTokens(
        email: String,
        accessToken: String,
        refreshToken: String?,
        expiryTime: Long
    ) {
        dataStore.updateData { current ->
            current.copy(
                email = email,
                accessToken = accessToken,
                refreshToken = refreshToken ?: current.refreshToken,
                expiryTime = expiryTime
            )
        }
    }

    suspend fun login(email: String) {
        // Fallback for demo mode
        dataStore.updateData {
            it.copy(
                email = email,
                accessToken = "demo_token",
                refreshToken = "demo_refresh",
                expiryTime = Long.MAX_VALUE
            )
        }
    }

    suspend fun logout() {
        dataStore.updateData { AuthData() }
    }

    suspend fun getEmail(): String? = dataStore.data.first().email
    suspend fun getAccessToken(): String? = dataStore.data.first().accessToken
    suspend fun getRefreshToken(): String? = dataStore.data.first().refreshToken
    
    fun isEmailValid(email: String): Boolean {
        return email.endsWith("@$REQUIRED_DOMAIN", ignoreCase = true)
    }

    suspend fun isTokenExpired(): Boolean {
        val data = dataStore.data.first()
        return isTokenExpired(data)
    }

    private fun isTokenExpired(data: AuthData): Boolean {
        val expiry = data.expiryTime
        if (expiry == 0L || expiry == Long.MAX_VALUE) return false
        
        // Buffer of 5 minutes
        return timeProvider.now() > (expiry - 5 * 60 * 1000)
    }

    suspend fun silentRefresh(): Boolean = withContext(Dispatchers.IO) {
        val refreshToken = getRefreshToken() ?: return@withContext false
        try {
            val response = GoogleRefreshTokenRequest(
                transport,
                jsonFactory,
                refreshToken,
                CLIENT_ID,
                CLIENT_SECRET
            ).execute()

            saveTokens(
                email = getEmail() ?: "",
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: refreshToken,
                expiryTime = timeProvider.now() + (response.expiresInSeconds * 1000)
            )
            true
        } catch (e: Exception) {
            // We don't need to manually update _authState here anymore, 
            // the authState Flow will automatically emit EXPIRED if isTokenExpired becomes true
            // or if accessToken is cleared. But if refresh fails, we might want to clear tokens?
            // The original code did: _authState.value = AuthState.EXPIRED
            // To achieve this, we might need a way to mark it as explicitly expired even if time hasn't passed,
            // but usually a failed refresh means we are unauthenticated or need login.
            false
        }
    }

    fun setTransientToken(token: String) {
        // No longer used
    }
}
