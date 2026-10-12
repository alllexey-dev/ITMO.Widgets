package dev.alllexey.itmowidgets.feature.weblogin.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/** What a pairing connects; every kind but [WEB_SESSION] carries a device key and receives an ITMO session. */
enum class PairingKind { WEB_SESSION, AGENT, AGENT_SESSION, WEB_ITMO }

/**
 * The plaintext sealed to a device: the phone's previous ITMO refresh token, which the device refreshes at once. It
 * exists only between the hand-off and the seal; never logged or saved, so [toString] is redacted.
 */
@Serializable
class PairingBundle(
    val challengeId: Uuid,
    val kind: PairingKind,
    val isu: Int,
    val refreshToken: String,
    val refreshExpiresAt: Instant,
    val issuedAt: Instant,
    val v: Int = VERSION,
) {
    override fun toString(): String = "PairingBundle(redacted)"

    companion object {
        const val VERSION = 1
    }
}

/** The refresh token the phone stopped using, with its expiry; handed out once, so [toString] is redacted. */
class HandoffToken(val refreshToken: String, val refreshExpiresAt: Instant) {
    override fun toString(): String = "HandoffToken(redacted)"
}

/**
 * The v1 ITMO session hand-off: the phone refreshes its own session once, keeps the new tokens and hands out the
 * refresh token it held before. ITMO.ID does not revoke a rotated refresh token, so the device continues on that one.
 */
fun interface HandoffSession {
    suspend fun rotate(): AppResult<HandoffToken>
}

/**
 * HPKE (RFC 9180) base mode with DHKEM(X25519, HKDF-SHA256), HKDF-SHA256 and ChaCha20-Poly1305 and an empty AAD: Tink
 * on Android, CryptoKit on iOS. Returns the encapsulated key (32 bytes) followed by the ciphertext.
 */
fun interface HpkeSender {
    fun seal(recipientPublicKey: ByteArray, info: ByteArray, plaintext: ByteArray): ByteArray
}

/** The phone's screen lock, asked before an ITMO session leaves the phone. */
fun interface DeviceUnlock {
    suspend fun unlock(): DeviceUnlockResult
}

enum class DeviceUnlockResult {
    UNLOCKED,
    CANCELLED,

    /** No screen lock is set, so there is nothing to confirm with. */
    NOT_SET_UP,
}
