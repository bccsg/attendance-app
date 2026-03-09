package sg.org.bcc.attendance.util

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val aead: Aead by lazy {
        AeadConfig.register()
        
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "tink_keyset", "tink_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://tink_master_key")
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    fun encrypt(data: ByteArray, associatedData: ByteArray? = null): ByteArray {
        return aead.encrypt(data, associatedData)
    }

    fun decrypt(encryptedData: ByteArray, associatedData: ByteArray? = null): ByteArray {
        return aead.decrypt(encryptedData, associatedData)
    }
}
