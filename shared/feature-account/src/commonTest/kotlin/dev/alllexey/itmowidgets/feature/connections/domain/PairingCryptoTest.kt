package dev.alllexey.itmowidgets.feature.connections.domain

import dev.alllexey.itmowidgets.feature.connections.domain.PairingVectors.CHALLENGE_ID
import dev.alllexey.itmowidgets.feature.connections.domain.PairingVectors.SECRET
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun theInfoOfARenewalNamesItsKind() {
        val info = PairingCrypto.info(CHALLENGE_ID, PairingKind.RENEW, SECRET).toHexString()

        assertEquals(PairingVectors.INFO_HEX.replace("434f4e4e454354", "52454e4557"), info)
    }

    @Test
    fun theInfoBindsTheChallengeTheKindAndTheSecret() {
        assertEquals(PairingVectors.INFO_HEX, PairingCrypto.info(CHALLENGE_ID, PairingKind.CONNECT, SECRET).toHexString())
    }
}
