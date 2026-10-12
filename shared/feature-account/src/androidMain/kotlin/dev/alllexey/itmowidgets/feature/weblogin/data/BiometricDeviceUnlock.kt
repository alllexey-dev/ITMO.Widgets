package dev.alllexey.itmowidgets.feature.weblogin.data

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.pair_unlock_reason
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString

/**
 * [DeviceUnlock] with `BiometricPrompt`: a fingerprint or face, or the PIN, pattern or password. `BIOMETRIC_WEAK`
 * because this confirms the owner's presence and unlocks no key; with `DEVICE_CREDENTIAL` it works from API 26 on.
 * Lives as long as the [activity]'s screen that asks; an error or a dismissal is [DeviceUnlockResult.CANCELLED].
 */
class BiometricDeviceUnlock(private val activity: FragmentActivity) : DeviceUnlock {

    override suspend fun unlock(): DeviceUnlockResult {
        if (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            return DeviceUnlockResult.NOT_SET_UP
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(Res.string.pair_unlock_reason))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        return suspendCancellableCoroutine { continuation ->
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(DeviceUnlockResult.UNLOCKED)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(DeviceUnlockResult.CANCELLED)
                }
            }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(info)
        }
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
