package dev.alllexey.itmowidgets.feature.weblogin.domain

import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingVectors.CHALLENGE_ID
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingVectors.SECRET
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** Expected values computed independently with Python's `hashlib` from the formulas of the pairing design. */
class PairingCryptoTest {
    private val publicKey = PairingVectors.PUBLIC_KEY_HEX.hexToByteArray()

    @Test
    fun theCommitmentHashesTheKeyAndSecret() {
        assertEquals(
            "20758b0aa1a0d853c836e33b6295d0f4b96108abbeca9699419f52014a03f665",
            PairingCrypto.keyCommitment(publicKey, SECRET).toHexString(),
        )
    }

    @Test
    fun theInfoBindsTheChallengeTheKindAndTheSecret() {
        assertEquals(PairingVectors.INFO_HEX, PairingCrypto.info(CHALLENGE_ID, PairingKind.AGENT, SECRET).toHexString())
    }

    @Test
    fun theVerificationCodeIsSixDigitsFromTheFirstTwentyBits() {
        assertEquals("765323", PairingCrypto.verificationCode(CHALLENGE_ID, publicKey, SECRET))
        // 20 bits reach 1_048_575: this one is 1_003_981, so it wraps and keeps its leading zeros.
        val challenge = Uuid.parse("00000000-0000-0000-0000-000000000001")
        assertEquals("003981", PairingCrypto.verificationCode(challenge, publicKey, ByteArray(16) { 42 }))
    }
}
