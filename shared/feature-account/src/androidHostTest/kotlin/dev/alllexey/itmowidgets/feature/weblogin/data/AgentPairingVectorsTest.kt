package dev.alllexey.itmowidgets.feature.weblogin.data

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HpkePrivateKey
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.util.Bytes
import com.google.crypto.tink.util.SecretBytes
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingCrypto
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingKind
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingVectors
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The phone and the self-hosted agent (`itmo-widgets-agent` d8513c9, `internal/pair/crypto.go`) agree on
 * [PairingVectors]: the commitment, the verification code and the HPKE `info` below are what the agent's
 * `Commitment`, `SAS` and `Info` print for that key, secret and challenge; [SEALED_BY_GO] is the agent's suite
 * (`crypto/hpke`, DHKEM X25519 / HKDF-SHA256 / ChaCha20-Poly1305) sealing [PairingVectors.BUNDLE_JSON]. The agent's
 * `Target.Open` opened [SEALED_BY_TINK] (this app's sealer) and `SEALED_BY_CRYPTOKIT` when these were recorded.
 */
class AgentPairingVectorsTest {
    private val link = PairingVectors.link()

    @Test
    fun theCommitmentVerificationCodeAndInfoAreTheAgents() {
        val commitment = BASE64.encode(PairingCrypto.keyCommitment(link.publicKey, link.secret))
        val sas = PairingCrypto.verificationCode(PairingVectors.CHALLENGE_ID, link.publicKey, link.secret)
        val info = PairingCrypto.info(PairingVectors.CHALLENGE_ID, PairingKind.AGENT, link.secret)

        assertEquals(AGENT_COMMITMENT, commitment)
        assertEquals(AGENT_SAS, "${sas.take(3)} ${sas.drop(3)}")
        assertEquals(AGENT_INFO_HEX, info.toHexString())
    }

    @Test
    fun tinkOpensWhatTheAgentsSuiteSealedAndWhatTheAgentOpened() {
        val info = PairingVectors.INFO_HEX.hexToByteArray()

        assertEquals(PairingVectors.BUNDLE_JSON, open(SEALED_BY_GO.hexToByteArray(), info))
        assertEquals(PairingVectors.BUNDLE_JSON, open(BASE64.decode(SEALED_BY_TINK), info))
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

        const val AGENT_COMMITMENT = "IHWLCqGg2FPINuM7YpXQ9LlhCKu-ypaZQZ9SAUoD9mU"
        const val AGENT_SAS = "765 323"
        const val AGENT_INFO_HEX = "69746d6f2d776964676574732070616972207631000190f1a23b4c7d5e8f60718293a4b5c6" +
            "4147454e5400000102030405060708090a0b0c0d0e0f"

        const val SEALED_BY_GO = "0fb038e3b0f5bf7243d04c9fafbd184dca7b325ba2fc2720986aeedfc6896d2d96b17d8c68f7201b8c308ab2a25c0593" +
            "f545520da1b95b3d993ec3ff4bcc2a89fa1f9687948408fb44cb1f0500207abcd77917bc9f351d847fe30e9a9fc35cf8" +
            "e28c9aa2295a34ff0075e3fc2cdba25db435e5bb8df1f09bb15947e47c1023b6dfae812f606f085ad754b8770b3532e2" +
            "ed5fdc7b9fd737df5a5766324ec023df7422e1fec35aa0a6c7fe772b30673e2aca3710f3e252266aba492187f75cf52c" +
            "e7031a92b3770ba66386f4ff3ae75dac0bffcc1c5d2ae9f44c05c3cf39ab74fa36dfcc077d6dd9c1396ce919261cfec3" +
            "f800661c3959e2bc"

        const val SEALED_BY_TINK = "BE8s1QiX1pvXdVhxsiGFS3wQp_XpK7rKnQta_TG6wi4tLbdDj1yV7HXJHK7H82ANe0kkqGEU5PMsyonAtp4db46zyNa5IU8j" +
            "S2oVMa3YRGhyNCsrISXeMEG2IybkLWD_WMFko5YXAigPNQf6sh2ffg5i56T8KMqmH2OTuAxhFkla1XNI4MyLhRhiIHFF4H-c" +
            "M3DUQ89EOCSkACDE6Tshb27zit0fUIfqI3lyDSDiiUmVYmpAHmPPHKyLbAdgFejfEXs557GTLOfx5a2od_c43oG20K4YnRHM" +
            "DrdIMGJ0vtTER05yRpnCVE1ewrvn5JSwRzukF0Nmlp0"
    }
}
