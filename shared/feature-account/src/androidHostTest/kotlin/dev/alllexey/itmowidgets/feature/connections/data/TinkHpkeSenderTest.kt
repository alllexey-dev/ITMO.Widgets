package dev.alllexey.itmowidgets.feature.connections.data

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkePrivateKey
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.util.Bytes
import com.google.crypto.tink.util.SecretBytes
import dev.alllexey.itmowidgets.feature.connections.domain.PairingCrypto
import dev.alllexey.itmowidgets.feature.connections.domain.PairingKind
import dev.alllexey.itmowidgets.feature.connections.domain.PairingSealer
import dev.alllexey.itmowidgets.feature.connections.domain.PairingVectors
import java.security.GeneralSecurityException
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Tink as the client: it opens what the phone's Tink sealer and what iOS's CryptoKit sealed (the fixed interop
 * vector of [PairingVectors]), and nothing sealed for another challenge, kind or secret. The agent opens the same way.
 */
class TinkHpkeSenderTest {
    private val sealer = PairingSealer(TinkHpkeSender())

    @Test
    fun theDeviceOpensTheBundleTheAndroidSenderSealed() {
        val sealed = sealer.sealBundle(PairingVectors.link(), PairingVectors.BUNDLE)

        assertEquals(PairingVectors.BUNDLE_JSON, open(BASE64.decode(sealed), PairingVectors.INFO_HEX.hexToByteArray()))
        // HPKE's ephemeral key makes every seal different.
        assertNotEquals(sealed, sealer.sealBundle(PairingVectors.link(), PairingVectors.BUNDLE))
    }

    @Test
    fun theDeviceOpensTheBundleCryptoKitSealed() {
        val sealed = BASE64.decode(PairingVectors.SEALED_BY_CRYPTOKIT)

        assertEquals(PairingVectors.INTEROP_PLAINTEXT, open(sealed, PairingVectors.INTEROP_INFO_HEX.hexToByteArray()))
    }

    @Test
    fun aBundleForAnotherChallengeKindOrSecretDoesNotOpen() {
        val sealed = BASE64.decode(sealer.sealBundle(PairingVectors.link(), PairingVectors.BUNDLE))
        val secret = PairingVectors.SECRET
        listOf(
            PairingCrypto.info(kotlin.uuid.Uuid.random(), PairingKind.CONNECT, secret),
            PairingCrypto.info(PairingVectors.CHALLENGE_ID, PairingKind.RENEW, secret),
            PairingCrypto.info(PairingVectors.CHALLENGE_ID, PairingKind.CONNECT, ByteArray(16)),
        ).forEach { info -> assertFailsWith<GeneralSecurityException> { open(sealed, info) } }
    }

    private fun open(sealed: ByteArray, info: ByteArray): String {
        val publicKey = HpkePublicKey.create(
            TinkHpkeSender.PARAMETERS, Bytes.copyFrom(PairingVectors.PUBLIC_KEY_HEX.hexToByteArray()), null,
        )
        val privateKey = HpkePrivateKey.create(
            publicKey,
            SecretBytes.copyFrom(PairingVectors.PRIVATE_KEY_HEX.hexToByteArray(), InsecureSecretKeyAccess.get()),
        )
        val keyset = KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.importKey(privateKey).withRandomId().makePrimary())
            .build()
        return keyset.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java).decrypt(sealed, info)
            .decodeToString()
    }

    private companion object {
        val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    }
}
