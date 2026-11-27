package dev.alllexey.itmowidgets.feature.connections.domain

import kotlin.uuid.Uuid
import okio.Buffer

/**
 * The byte strings both ends of a pairing derive from the link's public key `pk` and secret `s`. Backend sees only
 * [keyCommitment]; it cannot compute the HPKE [info].
 */
object PairingCrypto {

    /** `SHA-256("iw-pair-commit-v1" || pk || s)`: Backend stores it, the phone checks the scanned link against it. */
    fun keyCommitment(publicKey: ByteArray, secret: ByteArray): ByteArray =
        sha256("iw-pair-commit-v1".encodeToByteArray(), publicKey, secret)

    /**
     * `"itmo-widgets pair v1" || 0x00 || challengeId || kind || 0x00 || s`: binds a ciphertext to one challenge and
     * kind, and only a sender that read the link (knows `s`) seals what the client opens. The agent and itmo-mcp
     * compute exactly this; Backend's `connections.md` omits the trailing `0x00 || s`.
     */
    fun info(challengeId: Uuid, kind: PairingKind, secret: ByteArray): ByteArray = Buffer()
        .writeUtf8("itmo-widgets pair v1").writeByte(0)
        .write(challengeId.toByteArray())
        .writeUtf8(kind.name).writeByte(0)
        .write(secret)
        .readByteArray()

    private fun sha256(vararg parts: ByteArray): ByteArray =
        Buffer().apply { parts.forEach(::write) }.sha256().toByteArray()
}
