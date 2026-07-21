package de.oliveroehme.campaignfield.network.auth

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class AesGcmCookieCipher {
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val nonce = cipher.iv
        check(nonce.size == NONCE_BYTES) { "Unerwartete AES-GCM-Nonce-Länge." }
        cipher.updateAAD(ASSOCIATED_DATA)
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(FORMAT_VERSION) + nonce + ciphertext
    }

    fun decrypt(envelope: ByteArray, key: SecretKey): ByteArray {
        require(envelope.size >= 1 + NONCE_BYTES + TAG_BYTES) { "Ungültiger Cookie-Speicher." }
        require(envelope.first() == FORMAT_VERSION) { "Unbekannte Cookie-Speicherversion." }
        val nonce = envelope.copyOfRange(1, 1 + NONCE_BYTES)
        val ciphertext = envelope.copyOfRange(1 + NONCE_BYTES, envelope.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(ASSOCIATED_DATA)
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        const val FORMAT_VERSION: Byte = 1
        val ASSOCIATED_DATA = "campaign-field/cookies/v1".toByteArray(Charsets.UTF_8)
    }
}
