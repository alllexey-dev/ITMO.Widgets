package dev.alllexey.itmowidgets.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidKeystoreTokenCipherTest {

    private val cipher = AndroidKeystoreTokenCipher()

    @Test
    fun encryptAndDecryptRoundTrip() {
        val encrypted = cipher.encrypt("sensitive-token")

        assertNotEquals("sensitive-token", encrypted)
        assertEquals("sensitive-token", cipher.decrypt(encrypted))
    }

    @Test
    fun encryptionUsesRandomInitializationVector() {
        val first = cipher.encrypt("same-token")
        val second = cipher.encrypt("same-token")

        assertNotEquals(first, second)
    }

    @Test
    fun unsupportedPayloadIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            cipher.decrypt("plaintext-token")
        }
    }
}
