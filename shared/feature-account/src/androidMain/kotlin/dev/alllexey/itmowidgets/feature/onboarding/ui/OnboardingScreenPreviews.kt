package dev.alllexey.itmowidgets.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.components.services.ServicesConsentContent
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.ui.preview.OnboardingSamples

/*
 * The harness names a baseline `<function>_<@Preview name>`; each step is a function called `OnboardingScreen` in a
 * holder class of its own, at the design lab's axes and the answered states.
 */

@Composable
private fun OnboardingPreview(step: String) = ItmoPreview { OnboardingScreen(OnboardingSamples.state(step), OnboardingActions()) }

internal class OnboardingScreenValuePreview {
    @Preview(name = "value")
    @Composable
    fun OnboardingScreen() = OnboardingPreview("value")
}

internal class OnboardingScreenValueEmptyPreview {
    @Preview(name = "value-empty")
    @Composable
    fun OnboardingScreen() = OnboardingPreview("value-empty")
}

internal class OnboardingScreenPermissionsPreview {
    @Preview(name = "permissions")
    @Composable
    fun OnboardingScreen() = OnboardingPreview("permissions")
}

internal class OnboardingScreenPermissionsNonePreview {
    @Preview(name = "permissions-none")
    @Composable
    fun OnboardingScreen() = OnboardingPreview("permissions-none")
}

internal class OnboardingScreenConnectionPreview {
    @Preview(name = "connection")
    @Composable
    fun OnboardingScreen() = OnboardingPreview("connection")
}

internal class OnboardingScreenConnectedPreview {
    @Preview(name = "connected")
    @Composable
    fun OnboardingScreen() = OnboardingPreview("connected")
}

/** The consent sheet the last step opens, on the sheet's colour. */
internal class OnboardingScreenConsentPreview {
    @Preview(name = "consent", heightDp = 900)
    @Composable
    fun OnboardingScreen() = ItmoPreview {
        Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) { ServicesConsentContent(onConnect = {}, onDismiss = {}) }
    }
}
