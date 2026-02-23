package sg.org.bcc.attendance.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val _isAuthed = MutableStateFlow(prefs.getBoolean("is_authed", false))
    val isAuthed: StateFlow<Boolean> = _isAuthed.asStateFlow()

    fun login(email: String, displayName: String) {
        prefs.edit()
            .putBoolean("is_authed", true)
            .putString("email", email)
            .putString("display_name", displayName)
            .apply()
        _isAuthed.value = true
    }

    fun logout() {
        prefs.edit().clear().apply()
        _isAuthed.value = false
    }

    fun getEmail(): String? = prefs.getString("email", null)
    fun getDisplayName(): String? = prefs.getString("display_name", null)
    
    // For future implementation of token expiry
    fun isTokenExpired(): Boolean = false 
}
