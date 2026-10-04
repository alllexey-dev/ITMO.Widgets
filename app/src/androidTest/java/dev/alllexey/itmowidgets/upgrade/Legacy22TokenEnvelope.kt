package dev.alllexey.itmowidgets.upgrade

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Frozen copy of the 2.2 `v1:` token envelope (`AndroidKeystoreTokenCipher.encrypt` at tag `v2.2`): AES/GCM under
 * the Keystore alias `itmo_widgets_myitmo_tokens_v1`, payload `ivLength(1 byte) | iv | ciphertext+tag`, Base64
 * without wrapping. A Keystore key never leaves its device, so the fixture holds plaintext and this envelope seals it
 * on the device under test. Never change it to follow head: it is what 2.2 left on users' phones.
 */
object Legacy22TokenEnvelope {

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(cipher.iv.size.toByte())
            .put(cipher.iv)
            .put(encrypted)
            .array()
        return "$FORMAT_PREFIX${Base64.encodeToString(payload, Base64.NO_WRAP)}"
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE_BITS)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }
            .generateKey()
    }

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "itmo_widgets_myitmo_tokens_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val FORMAT_PREFIX = "v1:"
    private const val KEY_SIZE_BITS = 256
}
