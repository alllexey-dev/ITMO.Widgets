package dev.alllexey.itmowidgets.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingStep
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.ui.preview.OnboardingPreviewSamples
import dev.alllexey.itmowidgets.feature.onboarding.ui.preview.OnboardingWidgetPreviewSample

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LA-1c recorded the XML references as
 * `OnboardingScreen_<state>`. Each state therefore is a function called `OnboardingScreen` in a holder class of its
 * own; the scanner instantiates each holder by reflection.
 */

@Composable
private fun OnboardingPreview(state: OnboardingUiState) = ItmoPreview {
    OnboardingScreen(state, OnboardingActions(), widgetPreview = { kind, modifier ->
        OnboardingWidgetPreviewSample(kind, modifier)
    })
}

internal class OnboardingScreenSingleLessonPreview {
    @Preview(name = "widget-single-lesson")
    @Composable
    fun OnboardingScreen() = OnboardingPreview(OnboardingPreviewSamples.state(OnboardingStep.COMPACT_WIDGET))
}

internal class OnboardingScreenDaySchedulePreview {
    @Preview(name = "widget-day-schedule")
    @Composable
    fun OnboardingScreen() = OnboardingPreview(OnboardingPreviewSamples.state(OnboardingStep.FULL_WIDGET))
}

internal class OnboardingScreenQrPreview {
    @Preview(name = "widget-qr")
    @Composable
    fun OnboardingScreen() = OnboardingPreview(OnboardingPreviewSamples.state(OnboardingStep.QR_WIDGET))
}

internal class OnboardingScreenNoPinningPreview {
    @Preview(name = "widget-no-pinning")
    @Composable
    fun OnboardingScreen() =
        OnboardingPreview(OnboardingPreviewSamples.state(OnboardingStep.COMPACT_WIDGET, pinSupported = false))
}

internal class OnboardingScreenServicesOffPreview {
    @Preview(name = "services-off")
    @Composable
    fun OnboardingScreen() = OnboardingPreview(OnboardingPreviewSamples.state(OnboardingStep.SERVICES))
}

internal class OnboardingScreenServicesOnPreview {
    @Preview(name = "services-on")
    @Composable
    fun OnboardingScreen() =
        OnboardingPreview(OnboardingPreviewSamples.state(OnboardingStep.SERVICES, servicesEnabled = true))
}

internal class OnboardingScreenServicesBusyPreview {
    @Preview(name = "services-busy")
    @Composable
    fun OnboardingScreen() =
        OnboardingPreview(OnboardingPreviewSamples.state(OnboardingStep.SERVICES, servicesBusy = true))
}

internal class OnboardingScreenNotificationsAskPreview {
    @Preview(name = "notifications-ask")
    @Composable
    fun OnboardingScreen() =
        OnboardingPreview(OnboardingPreviewSamples.state(OnboardingStep.NOTIFICATIONS, servicesEnabled = true))
}

internal class OnboardingScreenNotificationsDeniedPreview {
    @Preview(name = "notifications-denied")
    @Composable
    fun OnboardingScreen() = OnboardingPreview(
        OnboardingPreviewSamples.state(OnboardingStep.NOTIFICATIONS, servicesEnabled = true, notificationsAsked = true),
    )
}

internal class OnboardingScreenNotificationsGrantedPreview {
    @Preview(name = "notifications-granted")
    @Composable
    fun OnboardingScreen() = OnboardingPreview(
        OnboardingPreviewSamples.state(OnboardingStep.NOTIFICATIONS, servicesEnabled = true, notificationsGranted = true),
    )
}
