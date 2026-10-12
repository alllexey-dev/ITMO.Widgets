package dev.alllexey.itmowidgets.feature.weblogin.data

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.UiTextResolver
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [DeviceUnlock] with `BiometricPrompt`: a fingerprint or face, or the PIN, pattern or password. `BIOMETRIC_WEAK`
 * because this confirms the owner's presence and unlocks no key; with `DEVICE_CREDENTIAL` it works from API 26 on.
 * Lives as long as the [activity]'s screen that asks; an error or a dismissal is [DeviceUnlockResult.CANCELLED].
 * [texts] resolves the prompt's title.
 */
class BiometricDeviceUnlock(
    private val activity: FragmentActivity,
    private val texts: UiTextResolver,
) : DeviceUnlock {

    override suspend fun unlock(): DeviceUnlockResult {
        if (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) != BiometricManager.BIOMETRIC_SUCCESS) {
            return DeviceUnlockResult.NOT_SET_UP
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(texts.resolve(UiText.Res(AccountStrings.pair_unlock_reason)))
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
