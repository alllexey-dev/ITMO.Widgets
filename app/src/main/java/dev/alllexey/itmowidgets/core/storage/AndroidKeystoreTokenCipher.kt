package dev.alllexey.itmowidgets.core.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidKeystoreTokenCipher @Inject constructor() : TokenCipher {

    override fun encrypt(value: String): String {
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

    override fun decrypt(value: String): String {
        require(value.startsWith(FORMAT_PREFIX)) {
            "Unsupported token encryption format"
        }
        val payload = Base64.decode(value.removePrefix(FORMAT_PREFIX), Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Encrypted token payload is empty" }

        val buffer = ByteBuffer.wrap(payload)
        val ivSize = buffer.get().toInt() and 0xFF
        require(ivSize in MIN_IV_SIZE..MAX_IV_SIZE && buffer.remaining() > ivSize) {
            "Encrypted token payload is malformed"
        }
        val iv = ByteArray(ivSize).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
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

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "itmo_widgets_myitmo_tokens_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_PREFIX = "v1:"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
        const val MIN_IV_SIZE = 12
        const val MAX_IV_SIZE = 32
    }
}
