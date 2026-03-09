package sg.org.bcc.attendance.data.remote

import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import sg.org.bcc.attendance.util.SecurityManager
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class AuthDataSerializer @Inject constructor(
    private val securityManager: SecurityManager
) : Serializer<AuthData> {
    override val defaultValue: AuthData = AuthData()

    override suspend fun readFrom(input: InputStream): AuthData {
        val encryptedData = input.readBytes()
        if (encryptedData.isEmpty()) {
            android.util.Log.d("AuthDataStore", "readFrom: Empty data, returning default")
            return defaultValue
        }
        
        return try {
            val decryptedData = securityManager.decrypt(encryptedData)
            android.util.Log.d("AuthDataStore", "readFrom: Decrypted data successfully")
            Json.decodeFromString(AuthData.serializer(), decryptedData.decodeToString())
        } catch (e: Exception) {
            android.util.Log.e("AuthDataStore", "readFrom: Failed to decrypt or parse", e)
            defaultValue
        }
    }

    override suspend fun writeTo(t: AuthData, output: OutputStream) {
        android.util.Log.d("AuthDataStore", "writeTo: Encrypting and saving auth data")
        val json = Json.encodeToString(AuthData.serializer(), t)
        val encryptedData = securityManager.encrypt(json.encodeToByteArray())
        output.write(encryptedData)
    }
}
