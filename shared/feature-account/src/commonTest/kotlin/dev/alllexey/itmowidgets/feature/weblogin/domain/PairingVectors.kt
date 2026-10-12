package dev.alllexey.itmowidgets.feature.weblogin.domain

import dev.alllexey.itmowidgets.core.weblogin.PairLink
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One fixed pairing shared by the Kotlin tests and `iosApp/Tests/UnitTests/PairingTests.swift`: the device key is
 * RFC 9180 A.2's recipient key pair, the secret the bytes 0..15, the token synthetic. Never a real token.
 */
internal object PairingVectors {
    const val PUBLIC_KEY_HEX = "4310ee97d88cc1f088a5576c77ab0cf5c3ac797f3d95139c6c84b5429c59662a"
    const val PRIVATE_KEY_HEX = "8057991eef8f1f1af18f4a9491d16a1ce333f695d4db8e38da75975c4478e0fb"
    const val LINK = "https://dev.widgets.alllexey.dev/pair#v1.ABCD2345." +
        "QxDul9iMwfCIpVdsd6sM9cOseX89lROcbIS1QpxZZio.AAECAwQFBgcICQoLDA0ODw"
    val CHALLENGE_ID = Uuid.parse("0190f1a2-3b4c-7d5e-8f60-718293a4b5c6")
    val SECRET = ByteArray(16) { it.toByte() }

    /** [PairingCrypto.info] for [CHALLENGE_ID], [PairingKind.AGENT] and [SECRET]. */
    const val INFO_HEX = "69746d6f2d776964676574732070616972207631000190f1a23b4c7d5e8f60718293a4b5c6" +
        "4147454e5400000102030405060708090a0b0c0d0e0f"

    val BUNDLE = PairingBundle(
        challengeId = CHALLENGE_ID,
        kind = PairingKind.AGENT,
        isu = 100001,
        refreshToken = "test-refresh-token",
        refreshExpiresAt = Instant.parse("2026-11-11T09:00:00Z"),
        issuedAt = Instant.parse("2026-10-12T09:00:00Z"),
    )

    /** [BUNDLE] as the sealer encodes it. */
    const val BUNDLE_JSON = """{"challengeId":"0190f1a2-3b4c-7d5e-8f60-718293a4b5c6","kind":"AGENT","isu":100001,""" +
        """"refreshToken":"test-refresh-token","refreshExpiresAt":"2026-11-11T09:00:00Z",""" +
        """"issuedAt":"2026-10-12T09:00:00Z","v":1}"""

    /** [BUNDLE_JSON] sealed by CryptoKit `HPKE.Sender` to [PUBLIC_KEY_HEX] with [INFO_HEX]; base64url. */
    const val SEALED_BY_CRYPTOKIT = "37YNlQqG71GoLEYtoCh_O3y1qIBGc44a1vRHTqjGlTyg2deA_vKEogu87KlNSEgK72MQCOG2pjrOoJ4N" +
        "YdnRYlyR2t40KcQ2Kiiv54uSoagvJSk9gTcr5XaFeAC9UjTq5NeClFEeaIlk-C78iAA7CH07bjKdoHNmXTn7WxI7eLol3kvYRYSTHuZUF33e" +
        "-1nhzdCX3BdRRg4QEjEXkQaVMF1PrNPM2V81hfRb-GvpW0tJknNXVSbFiyAW466IQ-JH1t6MQ7pOLzoZ4DyomqbzcprVQAy4kEno4QyTyczX" +
        "MgTW5jTmXp2MBcg8Gh4UwGHx93eI8lLdZ4I"

    fun link(): PairLink = checkNotNull(PairLink.parse(LINK))
}
