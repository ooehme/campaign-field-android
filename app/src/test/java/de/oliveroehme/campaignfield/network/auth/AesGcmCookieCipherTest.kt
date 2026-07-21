package de.oliveroehme.campaignfield.network.auth

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmCookieCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmCookieCipher()

    @Test
    fun `encrypts cookie data and restores it`() {
        val plaintext = "laravel_session=top-secret".encodeToByteArray()
        val encrypted = cipher.encrypt(plaintext, key)

        assertFalse(encrypted.decodeToString().contains("top-secret"))
        assertArrayEquals(plaintext, cipher.decrypt(encrypted, key))
    }

    @Test
    fun `rejects tampered cookie data`() {
        val encrypted = cipher.encrypt("secret".encodeToByteArray(), key)
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()

        assertThrows(AEADBadTagException::class.java) { cipher.decrypt(encrypted, key) }
    }
}
