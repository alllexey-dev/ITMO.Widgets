package dev.alllexey.itmowidgets.feature.connections.domain

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
    fun sealsABundleV2WithTheSameInfoAndTheItemsInOrder() {
        val hpke = RecordingHpke(output = ByteArray(64) { it.toByte() })

        PairingSealer(hpke).sealBundle(PairingVectors.link(), PairingVectors.BUNDLE_V2)

        assertEquals(PairingVectors.INFO_HEX, hpke.info?.toHexString())
        assertEquals(PairingVectors.BUNDLE_V2_JSON, hpke.plaintext?.decodeToString())
    }

    @Test
    fun theProtocol1BundleIsUnchanged() {
        val hpke = RecordingHpke(output = ByteArray(64))

        PairingSealer(hpke).sealBundle(PairingVectors.link(), PairingVectors.BUNDLE)

        assertFalse("items" in hpke.plaintext!!.decodeToString())
        assertEquals(PairingVectors.BUNDLE_JSON, hpke.plaintext?.decodeToString())
    }

    @Test
    fun aBundleV2NeverPrintsItsItems() {
        val printed = listOf(PairingVectors.BUNDLE_V2, PairingVectors.BUNDLE_V2.itmo) + PairingVectors.BUNDLE_V2.itmo.items

        for (value in printed) {
            assertFalse("test-refresh-token" in value.toString() || "test-identity-cookie" in value.toString(), "$value")
            assertFalse("8d1f0c2e" in value.toString(), "$value")
        }
    }

    @Test
    fun refusesACiphertextBackendWouldNotStore() {
        assertFailsWith<IllegalStateException> {
            PairingSealer(RecordingHpke(ByteArray(PairingSealer.MAX_SEALED_BYTES + 1)))
                .sealBundle(PairingVectors.link(), PairingVectors.BUNDLE)
        }
    }

    @Test
    fun theBundleNeverPrintsItsSession() {
        assertEquals("PairingBundle(redacted)", PairingVectors.BUNDLE.toString())
        assertFalse("test-refresh-token" in PairingVectors.BUNDLE.itmo.toString())
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
