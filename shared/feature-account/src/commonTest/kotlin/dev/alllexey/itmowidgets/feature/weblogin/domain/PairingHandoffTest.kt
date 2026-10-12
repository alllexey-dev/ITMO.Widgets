package dev.alllexey.itmowidgets.feature.weblogin.domain

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.testkit.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class PairingHandoffTest {
    private val clock = FakeClock(Instant.parse("2026-10-12T09:00:00Z"))
    private val challenge = PairingChallenge(PairingVectors.CHALLENGE_ID, PairingKind.AGENT, 100001)
    private val session = FakeSession(
        AppResult.Success(HandoffToken("test-refresh-token", Instant.parse("2026-11-11T09:00:00Z"))),
    )
    private val hpke = RecordingHpke()
    private val handoff = PairingHandoff(session, PairingSealer(hpke), clock)

    @Test
    fun anUnlockedPhoneSealsThePreviousTokenWithTheChallenge() = runTest {
        val result = handoff.seal({ DeviceUnlockResult.UNLOCKED }, PairingVectors.link(), challenge)

        assertIs<PairingHandoffResult.Sealed>(result)
        assertEquals(1, session.rotations)
        assertEquals(PairingVectors.BUNDLE_JSON, hpke.plaintext?.decodeToString())
    }

    @Test
    fun aCancelledOrMissingScreenLockRotatesNothing() = runTest {
        listOf(DeviceUnlockResult.CANCELLED, DeviceUnlockResult.NOT_SET_UP).forEach { unlock ->
            assertEquals(
                PairingHandoffResult.Locked(unlock),
                handoff.seal({ unlock }, PairingVectors.link(), challenge),
            )
        }
        assertEquals(0, session.rotations)
        assertEquals(null, hpke.plaintext)
    }

    @Test
    fun aFailedRotationSealsNothing() = runTest {
        val rejected = PairingHandoff(FakeSession(AppResult.Failure(AppError.Unauthorized)), PairingSealer(hpke), clock)

        assertEquals(
            PairingHandoffResult.Failed(AppError.Unauthorized),
            rejected.seal({ DeviceUnlockResult.UNLOCKED }, PairingVectors.link(), challenge),
        )
        assertEquals(null, hpke.plaintext)
    }

    @Test
    fun aWebSessionNeverReceivesAnItmoSession() = runTest {
        assertFailsWith<IllegalArgumentException> {
            val web = challenge.copy(kind = PairingKind.WEB_SESSION)
            handoff.seal({ DeviceUnlockResult.UNLOCKED }, PairingVectors.link(), web)
        }
        assertEquals(0, session.rotations)
    }

    private class FakeSession(private val result: AppResult<HandoffToken>) : HandoffSession {
        var rotations = 0

        override suspend fun rotate(): AppResult<HandoffToken> = result.also { rotations += 1 }
    }

    private class RecordingHpke : HpkeSender {
        var plaintext: ByteArray? = null

        override fun seal(recipientPublicKey: ByteArray, info: ByteArray, plaintext: ByteArray): ByteArray {
            this.plaintext = plaintext
            return ByteArray(48 + plaintext.size)
        }
    }
}
