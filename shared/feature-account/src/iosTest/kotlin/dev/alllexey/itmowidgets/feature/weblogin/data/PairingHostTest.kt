package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlockResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData

/** The Kotlin side of [PairingHost] with a fake host: bytes cross as `NSData` unchanged, answers map one to one. */
class PairingHostTest {

    @Test
    fun bytesReachTheHostAndComeBackUnchanged() {
        val host = FakeHost(sealed = true)

        val sealed = IosHpkeSender(host).seal(byteArrayOf(1, 2, 3), byteArrayOf(4), ByteArray(300) { it.toByte() })

        assertEquals(listOf(3, 1, 300), host.lengths)
        assertContentEquals(byteArrayOf(1, 2, 3) + ByteArray(300) { it.toByte() }, sealed)
    }

    @Test
    fun aKeyCryptoKitRefusesFails() {
        val sender = IosHpkeSender(FakeHost(sealed = false))

        assertFailsWith<IllegalStateException> { sender.seal(ByteArray(31), ByteArray(1), ByteArray(1)) }
    }

    @Test
    fun theScreenLockAnswersMapToTheResult() = runTest {
        assertEquals(DeviceUnlockResult.NOT_SET_UP, IosDeviceUnlock(FakeHost(passcode = false)).unlock())
        assertEquals(DeviceUnlockResult.UNLOCKED, IosDeviceUnlock(FakeHost(unlocks = true)).unlock())
        assertEquals(DeviceUnlockResult.CANCELLED, IosDeviceUnlock(FakeHost(unlocks = false)).unlock())
    }

    /** Seals by concatenating key and plaintext, so the test sees what crossed. */
    private class FakeHost(
        private val sealed: Boolean = true,
        private val passcode: Boolean = true,
        private val unlocks: Boolean = true,
    ) : PairingHost {
        val lengths = mutableListOf<Int>()

        override fun hpkeSeal(recipientPublicKey: NSData, info: NSData, plaintext: NSData): NSData? {
            lengths += listOf(recipientPublicKey, info, plaintext).map { it.length.toInt() }
            return if (sealed) (recipientPublicKey.toByteArray() + plaintext.toByteArray()).toNSData() else null
        }

        override fun canUnlockDevice(): Boolean = passcode

        override fun unlockDevice(reason: String, completion: (Boolean) -> Unit) = completion(unlocks)
    }
}
