package dev.alllexey.itmowidgets.feature.weblogin.domain

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.weblogin.PairLink
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * What «Подключить» does for a device that receives an ITMO session: the screen lock first, then the session
 * hand-off, then the seal to the link's key. Nothing rotates before the unlock, and the handed-out token leaves this
 * class only inside the ciphertext. The bundle names the signed-in ISU, which the device checks against its token.
 */
class PairingHandoff(
    private val session: HandoffSession,
    private val sealer: PairingSealer,
    private val users: CurrentUserProvider,
    private val clock: Clock,
) {

    /** [onUnlocked] runs once the owner confirmed, right before the session rotates. */
    suspend fun seal(
        unlock: DeviceUnlock,
        link: PairLink,
        challengeId: Uuid,
        kind: PairingKind,
        onUnlocked: () -> Unit = {},
    ): PairingHandoffResult {
        require(kind != PairingKind.WEB_SESSION) { "A web session receives no ITMO session" }
        val unlocked = unlock.unlock()
        if (unlocked != DeviceUnlockResult.UNLOCKED) return PairingHandoffResult.Locked(unlocked)
        val isu = users.getCurrentUser()?.isu ?: return PairingHandoffResult.Failed(AppError.Unauthorized)
        onUnlocked()
        val token = when (val rotated = session.rotate()) {
            is AppResult.Success -> rotated.value
            is AppResult.Failure -> return PairingHandoffResult.Failed(rotated.error)
        }
        val bundle = PairingBundle(challengeId, kind, isu, token.refreshToken, token.refreshExpiresAt, clock.now())
        return try {
            PairingHandoffResult.Sealed(sealer.sealBundle(link, bundle))
        } catch (error: Exception) {
            // A key the HPKE library refuses; the rotation already happened and is harmless.
            PairingHandoffResult.Failed(AppError.Unknown(error))
        }
    }
}

sealed interface PairingHandoffResult {

    /** `base64url(enc || ciphertext)` for the approval. */
    class Sealed(val sealedBundle: String) : PairingHandoffResult

    data class Locked(val unlock: DeviceUnlockResult) : PairingHandoffResult

    data class Failed(val error: AppError) : PairingHandoffResult
}
