package dev.alllexey.itmowidgets.feature.weblogin.domain

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PairingSealerTest {

    @Test
    fun sealsTheBundleJsonToTheLinksKeyWithThePairingInfo() {
        val hpke = RecordingHpke(output = ByteArray(64) { it.toByte() })

        val sealed = PairingSealer(hpke).sealBundle(PairingVectors.link(), PairingVectors.BUNDLE)

        assertContentEquals(PairingVectors.PUBLIC_KEY_HEX.hexToByteArray(), hpke.publicKey)
        assertEquals(PairingVectors.INFO_HEX, hpke.info?.toHexString())
        assertEquals(PairingVectors.BUNDLE_JSON, hpke.plaintext?.decodeToString())
        assertEquals(Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(hpke.output), sealed)
    }

    @Test
    fun refusesACiphertextBackendWouldNotStore() {
        assertFailsWith<IllegalStateException> {
            PairingSealer(RecordingHpke(ByteArray(PairingSealer.MAX_SEALED_BYTES + 1)))
                .sealBundle(PairingVectors.link(), PairingVectors.BUNDLE)
        }
    }

    @Test
    fun theBundleNeverPrintsItsToken() {
        assertEquals("PairingBundle(redacted)", PairingVectors.BUNDLE.toString())
        val token = HandoffToken("test-refresh-token", PairingVectors.BUNDLE.issuedAt)
        assertFalse("test-refresh-token" in token.toString())
    }

    private class RecordingHpke(val output: ByteArray) : HpkeSender {
        var publicKey: ByteArray? = null
        var info: ByteArray? = null
        var plaintext: ByteArray? = null

        override fun seal(recipientPublicKey: ByteArray, info: ByteArray, plaintext: ByteArray): ByteArray {
            publicKey = recipientPublicKey
            this.info = info
            this.plaintext = plaintext
            return output
        }
    }
}
