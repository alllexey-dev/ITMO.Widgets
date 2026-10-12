package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.weblogin.domain.HpkeSender
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.pair_unlock_reason
import kotlin.coroutines.resume
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * What pairing needs from frameworks only Swift reaches: CryptoKit's HPKE and LocalAuthentication. The app's
 * `IosPlatform` implements it once.
 */
interface PairingHost {

    /**
     * `HPKE.Sender` with `.Curve25519_SHA256_ChachaPoly` to [recipientPublicKey] (raw X25519) and [info], one `seal`
     * with an empty AAD: the encapsulated key followed by the ciphertext, or null when CryptoKit refuses the key.
     */
    fun hpkeSeal(recipientPublicKey: NSData, info: NSData, plaintext: NSData): NSData?

    /** `LAContext.canEvaluatePolicy(.deviceOwnerAuthentication)`: false without a passcode. */
    fun canUnlockDevice(): Boolean

    /** `LAContext.evaluatePolicy(.deviceOwnerAuthentication)` with [reason]; calls [completion] once. */
    fun unlockDevice(reason: String, completion: (Boolean) -> Unit)
}

/** [HpkeSender] on CryptoKit through [host]. */
class IosHpkeSender(private val host: PairingHost) : HpkeSender {

    override fun seal(recipientPublicKey: ByteArray, info: ByteArray, plaintext: ByteArray): ByteArray {
        val sealed = host.hpkeSeal(recipientPublicKey.toNSData(), info.toNSData(), plaintext.toNSData())
        return checkNotNull(sealed) { "CryptoKit refused the key" }.toByteArray()
    }
}

/** [DeviceUnlock] with LocalAuthentication through [host]: Face ID, Touch ID or the passcode. */
class IosDeviceUnlock(private val host: PairingHost) : DeviceUnlock {

    override suspend fun unlock(): DeviceUnlockResult {
        if (!host.canUnlockDevice()) return DeviceUnlockResult.NOT_SET_UP
        val reason = getString(Res.string.pair_unlock_reason)
        val unlocked = suspendCancellableCoroutine { continuation ->
            host.unlockDevice(reason) { unlocked -> if (continuation.isActive) continuation.resume(unlocked) }
        }
        return if (unlocked) DeviceUnlockResult.UNLOCKED else DeviceUnlockResult.CANCELLED
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
}
