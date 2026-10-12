package dev.alllexey.itmowidgets.feature.weblogin.domain

import kotlin.uuid.Uuid
import okio.Buffer

/**
 * The byte strings both ends of a pairing derive from the link's public key `pk` and secret `s`. Backend sees only
 * [keyCommitment]; it can compute neither the HPKE [info] nor the [verificationCode].
 */
object PairingCrypto {

    /** `SHA-256("iw-pair-commit-v1" || pk || s)`: Backend stores it, the phone checks the scanned link against it. */
    fun keyCommitment(publicKey: ByteArray, secret: ByteArray): ByteArray =
        sha256("iw-pair-commit-v1".encodeToByteArray(), publicKey, secret)

    /**
     * `"itmo-widgets pair v1" || 0x00 || challengeId || kind || 0x00 || s`: binds a ciphertext to one challenge and
     * kind, and only a sender that read the link (knows `s`) seals what the device opens.
     */
    fun info(challengeId: Uuid, kind: PairingKind, secret: ByteArray): ByteArray = Buffer()
        .writeUtf8("itmo-widgets pair v1").writeByte(0)
        .write(challengeId.toByteArray())
        .writeUtf8(kind.name).writeByte(0)
        .write(secret)
        .readByteArray()

    /** Six digits: the first 20 bits of `SHA-256("iw-pair-sas-v1" || challengeId || pk || s)` modulo 10^6. */
    fun verificationCode(challengeId: Uuid, publicKey: ByteArray, secret: ByteArray): String {
        val hash = sha256("iw-pair-sas-v1".encodeToByteArray(), challengeId.toByteArray(), publicKey, secret)
        val bits = (hash[0].toInt() and 0xff shl 12) or (hash[1].toInt() and 0xff shl 4) or
            (hash[2].toInt() and 0xff shr 4)
        return (bits % 1_000_000).toString().padStart(6, '0')
    }

    private fun sha256(vararg parts: ByteArray): ByteArray =
        Buffer().apply { parts.forEach(::write) }.sha256().toByteArray()
}
