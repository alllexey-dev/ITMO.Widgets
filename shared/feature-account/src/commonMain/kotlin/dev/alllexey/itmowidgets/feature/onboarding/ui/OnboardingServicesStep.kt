package dev.alllexey.itmowidgets.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.navigation.ProjectLinks
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoSwitch
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsGroup
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsGroupFooter
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsRow
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.shared.designsystem.ic_brand_github
import dev.alllexey.itmowidgets.shared.designsystem.ic_exercise
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_notification
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_feature_friends
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_feature_notifications
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_feature_sport
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_gives_title
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_privacy_default
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_source_link
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_stored_device
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_stored_footer
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_stored_group
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_stored_identity
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_stored_schedule
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_stored_sport
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_stored_title
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_subtitle
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_title
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_toggle_description
import dev.alllexey.itmowidgets.shared.feature.account.onboarding_services_toggle_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * The fields the Backend stores about a user, one string per line in schema order (`users`, `user_groups`, `lessons`,
 * `user_sport_lessons` with `sport_*_sign_entries`, `devices`): a new table with user data adds a line here.
 */
private val StoredFields: List<StringResource> = listOf(
    Res.string.onboarding_services_stored_identity,
    Res.string.onboarding_services_stored_group,
    Res.string.onboarding_services_stored_schedule,
    Res.string.onboarding_services_stored_sport,
    Res.string.onboarding_services_stored_device,
)

private val Features: List<Pair<DrawableResource, StringResource>> = listOf(
    KitRes.drawable.ic_group to Res.string.onboarding_services_feature_friends,
    KitRes.drawable.ic_exercise to Res.string.onboarding_services_feature_sport,
    KitRes.drawable.ic_notification to Res.string.onboarding_services_feature_notifications,
)

/**
 * The Backend opt-in as one switch row, the privacy default under it, what it gives and what the server stores,
 * named in full, with the `Код сервера` link.
 */
@Composable
internal fun OnboardingServicesStep(state: OnboardingUiState, actions: OnboardingActions) {
    OnboardingPage(
        title = stringResource(Res.string.onboarding_services_title),
        subtitle = stringResource(Res.string.onboarding_services_subtitle),
    ) {
        SettingsGroup(
            Modifier.padding(top = ItmoTheme.spacing.group),
            footer = { SettingsGroupFooter(stringResource(Res.string.onboarding_services_privacy_default)) },
        ) {
            row("services") { ServicesRow(state, actions) }
        }
        SettingsGroup(
            Modifier.padding(top = ItmoTheme.spacing.group),
            title = stringResource(Res.string.onboarding_services_gives_title),
        ) {
            row("features") {
                Column(
                    Modifier.padding(ItmoTheme.spacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
                ) {
                    Features.forEach { (icon, text) -> FeatureRow(icon, stringResource(text)) }
                }
            }
        }
        SettingsGroup(
            Modifier.padding(top = ItmoTheme.spacing.group),
            title = stringResource(Res.string.onboarding_services_stored_title),
        ) {
            row("stored") { StoredData(actions) }
        }
    }
}

/**
 * `Подключиться` over the whole row, read as one switch. While the opt-in runs a spinner takes the switch's slot, so
 * the row never changes height, and the row ignores taps.
 */
@Composable
private fun ServicesRow(state: OnboardingUiState, actions: OnboardingActions) {
    SettingsRow(
        title = stringResource(Res.string.onboarding_services_toggle_title),
        modifier = Modifier
            .toggleable(
                value = state.servicesEnabled,
                enabled = !state.servicesBusy,
                role = Role.Switch,
                onValueChange = actions.onServicesEnabled,
            )
            .testTag(OnboardingTestTags.SERVICES_ROW),
        description = stringResource(Res.string.onboarding_services_toggle_description),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ItmoSwitch(
                checked = state.servicesEnabled,
                onCheckedChange = null,
                modifier = Modifier
                    .alpha(if (state.servicesBusy) 0f else 1f)
                    .testTag(OnboardingTestTags.SERVICES_SWITCH),
            )
            if (state.servicesBusy) {
                ItmoActivityIndicator(Modifier.size(ServicesProgressSize).testTag(OnboardingTestTags.SERVICES_PROGRESS))
            }
        }
    }
}

/** `Widget.ItmoWidgets.FeatureRow`: a 24 dp `onSurfaceVariant` icon, 12 dp, the text in `bodyLarge`. */
@Composable
private fun FeatureRow(icon: DrawableResource, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = FeatureIconSize), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(end = ItmoTheme.spacing.content).size(FeatureIconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
    }
}

@Composable
private fun StoredData(actions: OnboardingActions) {
    Column(Modifier.fillMaxWidth().padding(ItmoTheme.spacing.cardPadding)) {
        StoredFields.forEachIndexed { index, line ->
            Text(
                stringResource(line),
                Modifier.padding(top = if (index == 0) 0.dp else ItmoTheme.spacing.compact),
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(Res.string.onboarding_services_stored_footer),
            Modifier.padding(top = ItmoTheme.spacing.content),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyMedium,
        )
        ProgressButton(
            label = stringResource(Res.string.onboarding_services_source_link),
            onClick = { actions.onOpenLink(ProjectLinks.SERVICES_SOURCE_URL) },
            modifier = Modifier.padding(top = ItmoTheme.spacing.compact).testTag(OnboardingTestTags.SERVICES_SOURCE),
            style = ProgressButtonStyle.Tonal,
            icon = painterResource(KitRes.drawable.ic_brand_github),
        )
    }
}

/** `fragment_onboarding_services.xml`'s `app:indicatorSize`. */
private val ServicesProgressSize = 24.dp

private val FeatureIconSize = 24.dp
