package dev.alllexey.itmowidgets.feature.weblogin.domain

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.testkit.FakeClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class PairingHandoffTest {
    private val clock = FakeClock(Instant.parse("2026-10-12T09:00:00Z"))
    private val users = object : CurrentUserProvider {
        var isu: Int? = 100001

        override suspend fun getCurrentUser() = CurrentUser(isu, name = null, pictureUrl = null)
    }
    private val session = FakeSession(
        AppResult.Success(HandoffToken("test-refresh-token", Instant.parse("2026-11-11T09:00:00Z"))),
    )
    private val hpke = RecordingHpke()
    private val handoff = PairingHandoff(session, PairingSealer(hpke), users, clock)

    @Test
    fun anUnlockedPhoneSealsThePreviousTokenWithTheChallenge() = runTest {
        var unlocked = 0
        val result = handoff.seal(
            { DeviceUnlockResult.UNLOCKED }, PairingVectors.link(), PairingVectors.CHALLENGE_ID, PairingKind.AGENT,
            onUnlocked = { unlocked += 1 },
        )

        assertIs<PairingHandoffResult.Sealed>(result)
        assertEquals(1, unlocked)
        assertEquals(1, session.rotations)
        assertEquals(PairingVectors.BUNDLE_JSON, hpke.plaintext?.decodeToString())
    }

    @Test
    fun aCancelledOrMissingScreenLockRotatesNothing() = runTest {
        listOf(DeviceUnlockResult.CANCELLED, DeviceUnlockResult.NOT_SET_UP).forEach { unlock ->
            assertEquals(
                PairingHandoffResult.Locked(unlock),
                handoff.seal({ unlock }, PairingVectors.link(), PairingVectors.CHALLENGE_ID, PairingKind.AGENT),
            )
        }
        assertEquals(0, session.rotations)
        assertEquals(null, hpke.plaintext)
    }

    @Test
    fun aFailedRotationSealsNothing() = runTest {
        val rejected = PairingHandoff(FakeSession(AppResult.Failure(AppError.Unauthorized)), PairingSealer(hpke), users, clock)

        assertEquals(
            PairingHandoffResult.Failed(AppError.Unauthorized),
            rejected.seal({ DeviceUnlockResult.UNLOCKED }, PairingVectors.link(), PairingVectors.CHALLENGE_ID, PairingKind.AGENT),
        )
        assertEquals(null, hpke.plaintext)
    }

    @Test
    fun aWebSessionNeverReceivesAnItmoSession() = runTest {
        assertFailsWith<IllegalArgumentException> {
            handoff.seal(
                { DeviceUnlockResult.UNLOCKED }, PairingVectors.link(), PairingVectors.CHALLENGE_ID,
                PairingKind.WEB_SESSION,
            )
        }
        assertEquals(0, session.rotations)
    }

    @Test
    fun withoutASignedInIsuNothingRotates() = runTest {
        users.isu = null

        val result = handoff.seal(
            { DeviceUnlockResult.UNLOCKED }, PairingVectors.link(), PairingVectors.CHALLENGE_ID, PairingKind.AGENT,
        )

        assertEquals(PairingHandoffResult.Failed(AppError.Unauthorized), result)
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
