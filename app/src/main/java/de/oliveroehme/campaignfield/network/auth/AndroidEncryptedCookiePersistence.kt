package de.oliveroehme.campaignfield.network.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidEncryptedCookiePersistence(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val cipher: AesGcmCookieCipher = AesGcmCookieCipher(),
) : CookiePersistence {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun read(): ByteArray? {
        val encoded = preferences.getString(COOKIES_KEY, null) ?: return null
        return try {
            val envelope = Base64.decode(encoded, Base64.NO_WRAP)
            cipher.decrypt(envelope, getOrCreateKey())
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    override fun write(value: ByteArray) {
        val envelope = try {
            cipher.encrypt(value, getOrCreateKey())
        } catch (error: Exception) {
            throw IllegalStateException("Cookies konnten nicht sicher gespeichert werden.", error)
        }
        check(preferences.edit().putString(COOKIES_KEY, Base64.encodeToString(envelope, Base64.NO_WRAP)).commit()) {
            "Cookies konnten nicht gespeichert werden."
        }
    }

    @Synchronized
    override fun clear() {
        preferences.edit().remove(COOKIES_KEY).commit()
        try {
            keyStore().deleteEntry(keyAlias)
        } catch (_: Exception) {
            // Ohne verschlüsselten Blob enthält ein verbliebener Schlüssel keine Sessiondaten.
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "campaign_field_session_cookies_v1"
        const val PREFERENCES_NAME = "campaign_field_secure_session"
        const val COOKIES_KEY = "encrypted_cookies"
    }
}
