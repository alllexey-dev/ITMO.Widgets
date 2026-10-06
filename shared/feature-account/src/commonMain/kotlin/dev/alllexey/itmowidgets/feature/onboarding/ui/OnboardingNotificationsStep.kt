package dev.alllexey.itmowidgets.feature.onboarding.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.shared.designsystem.ic_notification
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_notifications_allow
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_notifications_configure
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_notifications_off
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_notifications_on
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_notifications_open_settings
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_notifications_subtitle
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_notifications_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * The notification permission, asked right after the opt-in that sends the pushes: one status row and one button
 * that keeps its place. `Разрешить` while the system can still show its dialog, `Открыть настройки` after a denial
 * (the system will not ask twice) or without a runtime permission, `Настроить в Android` once granted.
 */
@Composable
internal fun OnboardingNotificationsStep(
    state: OnboardingUiState,
    actions: OnboardingActions,
    notificationPermissionIsRuntime: Boolean,
) {
    val granted = state.notificationsGranted
    val canAsk = notificationPermissionIsRuntime && !state.notificationsAsked
    OnboardingPage(
        title = stringResource(Res.string.onboarding_notifications_title),
        subtitle = stringResource(Res.string.onboarding_notifications_subtitle),
    ) {
        OnboardingCard(Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.group)) {
            Column(Modifier.padding(ItmoTheme.spacing.cardPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(KitRes.drawable.ic_notification),
                        contentDescription = null,
                        modifier = Modifier.padding(end = ItmoTheme.spacing.group).size(StatusIconSize),
                        tint = ItmoTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            if (granted) Res.string.onboarding_notifications_on else Res.string.onboarding_notifications_off,
                        ),
                        Modifier.weight(1f).testTag(OnboardingTestTags.NOTIFICATIONS_STATUS),
                        color = if (granted) ItmoTheme.colorScheme.primary else ItmoTheme.colorScheme.onSurface,
                        style = ItmoTheme.typography.titleMedium,
                    )
                }
                ProgressButton(
                    label = stringResource(
                        when {
                            granted -> Res.string.onboarding_notifications_configure
                            canAsk -> Res.string.onboarding_notifications_allow
                            else -> Res.string.onboarding_notifications_open_settings
                        },
                    ),
                    onClick = actions.onRequestNotifications,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ItmoTheme.spacing.group)
                        .testTag(OnboardingTestTags.NOTIFICATIONS_BUTTON),
                    style = ProgressButtonStyle.Tonal,
                )
            }
        }
    }
}

private val StatusIconSize = 24.dp
