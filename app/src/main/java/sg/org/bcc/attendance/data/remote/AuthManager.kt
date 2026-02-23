package sg.org.bcc.attendance.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    @ApplicationContext context: Context,
    private val timeProvider: TimeProvider
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

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth_prefs_encrypted",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _authState = MutableStateFlow(calculateInitialAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isAuthed = MutableStateFlow(_authState.value == AuthState.AUTHENTICATED)
    val isAuthed: StateFlow<Boolean> = _isAuthed.asStateFlow()

    private fun calculateInitialAuthState(): AuthState {
        val accessToken = prefs.getString("access_token", null)
        android.util.Log.d("AttendanceAuth", "Restoring auth state. Token present: ${accessToken != null}")
        
        if (accessToken == null) return AuthState.UNAUTHENTICATED
        
        val expired = isTokenExpired()
        android.util.Log.d("AttendanceAuth", "Token expired: $expired")
        
        return if (expired) AuthState.EXPIRED else AuthState.AUTHENTICATED
    }

    private fun updateLegacyIsAuthed() {
        _isAuthed.value = _authState.value == AuthState.AUTHENTICATED
    }

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

    private fun saveTokens(
        email: String,
        accessToken: String,
        refreshToken: String?,
        expiryTime: Long
    ) {
        prefs.edit().apply {
            putString("email", email)
            putString("access_token", accessToken)
            if (refreshToken != null) {
                putString("refresh_token", refreshToken)
            }
            putLong("expiry_time", expiryTime)
            apply()
        }
        _authState.value = AuthState.AUTHENTICATED
        updateLegacyIsAuthed()
    }

    fun login(email: String) {
        // Fallback for demo mode
        prefs.edit()
            .putString("email", email)
            .putString("access_token", "demo_token")
            .putLong("expiry_time", Long.MAX_VALUE)
            .apply()
        
        _authState.value = AuthState.AUTHENTICATED
        updateLegacyIsAuthed()
    }

    fun logout() {
        prefs.edit().clear().apply()
        _authState.value = AuthState.UNAUTHENTICATED
        updateLegacyIsAuthed()
    }

    fun getEmail(): String? = prefs.getString("email", null)
    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)
    
    fun isEmailValid(email: String): Boolean {
        return email.endsWith("@$REQUIRED_DOMAIN", ignoreCase = true)
    }

    fun isTokenExpired(): Boolean {
        val expiry = prefs.getLong("expiry_time", 0)
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
            _authState.value = AuthState.EXPIRED
            updateLegacyIsAuthed()
            false
        }
    }

    fun setTransientToken(token: String) {
        // No longer used in web flow, but kept for interface compatibility if needed
    }
}
