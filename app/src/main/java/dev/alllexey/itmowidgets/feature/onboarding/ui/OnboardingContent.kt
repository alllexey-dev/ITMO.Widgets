package dev.alllexey.itmowidgets.feature.onboarding.ui

import android.content.Context
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.app.NotificationManagerCompat
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.permission.notificationPermissionIsRuntime
import dev.alllexey.itmowidgets.core.ui.permission.openAppNotificationSettings
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingEvent
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel

/** The flow's root tag, which the routing tests look up. */
@VisibleForTesting
const val ONBOARDING_ROOT_TEST_TAG = "onboarding_root"

/**
 * `OnboardingScreen` inside the system bars, tagged [ONBOARDING_ROOT_TEST_TAG]. The Compose shell's onboarding entry
 * calls it.
 */
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    actions: OnboardingActions,
    consentShown: Boolean,
    snackbars: SnackbarHostState,
) {
    OnboardingScreen(
        state = state,
        actions = actions,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .testTag(ONBOARDING_ROOT_TEST_TAG),
        consentShown = consentShown,
        snackbarHostState = snackbars,
    )
}

/** The flow's actions over [viewModel]; the consent sheet and the calendar permission are the host's. */
fun onboardingActions(
    viewModel: OnboardingViewModel,
    onShowConsent: (Boolean) -> Unit,
) = OnboardingActions(
    onNext = { viewModel.next() },
    onRequestNotifications = { viewModel.requestNotifications() },
    onAddCalendar = { viewModel.addLessonsToCalendar() },
    onConnect = { viewModel.connect() },
    onNotNow = { viewModel.notNow() },
    onDetails = { onShowConsent(true) },
    onConsent = {
        onShowConsent(false)
        viewModel.onConsent()
    },
    onDismissConsent = { onShowConsent(false) },
)

/** Back is a step back from the second step until the flow is finished; the host leaves it on its own then. */
val OnboardingUiState.canStepBack: Boolean get() = stepIndex > 0 && !finished

/**
 * What only Android does for one event: the notification permission or the settings page, the calendar permission,
 * the consent sheet and the snackbars. The Compose shell's onboarding entry calls it.
 */
fun Context.handleOnboardingEvent(
    event: OnboardingEvent,
    notificationPermission: ActivityResultLauncher<Unit>,
    askCalendar: () -> Unit,
    showConsent: () -> Unit,
    showSnackbar: (message: Int) -> Unit,
) {
    when (event) {
        OnboardingEvent.RequestNotificationPermission ->
            if (notificationPermissionIsRuntime(Build.VERSION.SDK_INT)) notificationPermission.launch(Unit)
            else openAppNotificationSettings()
        OnboardingEvent.OpenNotificationSettings -> openAppNotificationSettings()
        OnboardingEvent.RequestCalendarAccess -> askCalendar()
        OnboardingEvent.ShowConsent -> showConsent()
        is OnboardingEvent.ShowError -> showSnackbar(event.error.messageRes())
    }
}

/** Covers both the runtime permission and the system switch on older Android; hosts call it on every resume. */
fun OnboardingViewModel.onNotificationsEnabled(context: Context) {
    onNotificationPermission(NotificationManagerCompat.from(context).areNotificationsEnabled())
}
