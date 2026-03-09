package sg.org.bcc.attendance.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class AuthData(
    val email: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiryTime: Long = 0L
)
