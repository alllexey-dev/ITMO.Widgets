package dev.alllexey.itmowidgets.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.services.ServicesConsentSheet
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.PersonListRow
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingMe
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingStep
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_info
import dev.alllexey.itmowidgets.shared.designsystem.ic_notification
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** What a test or a host looks up on the first-run flow. */
object OnboardingTestTags {
    const val STEPS = "onboarding_steps"
    const val NEXT = "onboarding_next"
    const val NOT_NOW = "onboarding_not_now"
    const val ME = "onboarding_me"
    const val NOTIFICATIONS_BUTTON = "onboarding_notifications_button"
    const val NOTIFICATIONS_GRANTED = "onboarding_notifications_granted"
    const val CALENDAR_BUTTON = "onboarding_calendar_button"
    const val CALENDAR_GRANTED = "onboarding_calendar_granted"
    const val DETAILS = "onboarding_connection_details"
}

/**
 * The callbacks of the first-run flow, one per [dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel]
 * entry point. [onDetails] opens the consent sheet to read what the server keeps, without connecting.
 */
data class OnboardingActions(
    val onNext: () -> Unit = {},
    val onRequestNotifications: () -> Unit = {},
    val onAddCalendar: () -> Unit = {},
    val onConnect: () -> Unit = {},
    val onNotNow: () -> Unit = {},
    val onDetails: () -> Unit = {},
    val onConsent: () -> Unit = {},
    val onDismissConsent: () -> Unit = {},
)

/**
 * The first-run flow (owner, 2026-10-19): the step dots with `1 из 2`, the step's title (and line) over its scrolling
 * body, and the step's actions pinned at the bottom: `Далее` on the first (it works without any permission),
 * `Подключить` and `Не сейчас` on the last. [consentShown] is the consent sheet the host keeps. The [Surface] gives
 * every text without its own colour `onSurface`, so the dark theme draws none of it black.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
    consentShown: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = ItmoTheme.colorScheme.surface) {
            Column {
                StepHeader(state)
                AnimatedContent(state.step, Modifier.weight(1f).testTag(OnboardingTestTags.STEPS), label = "onboarding step") { step ->
                    when (step) {
                        OnboardingStep.PERMISSIONS -> StepBody(
                            AccountStrings.onboarding_permissions_title,
                            top = state.me?.let { me -> { MeCard(me) } },
                        ) { PermissionsStep(state, actions) }
                        OnboardingStep.CONNECTION -> StepBody(
                            AccountStrings.onboarding_connection_title,
                            AccountStrings.onboarding_connection_text,
                        ) { ConnectionStep(state, actions) }
                    }
                }
                Footer(state, actions)
            }
        }
        ItmoSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    if (consentShown) ServicesConsentSheet(onConnect = actions.onConsent, onDismiss = actions.onDismissConsent)
}

/** The dots (the current one wide) and `1 из 2`, read together. */
@Composable
private fun StepHeader(state: OnboardingUiState) {
    val progress = text(AccountStrings.onboarding_step_progress, state.stepIndex + 1, state.stepCount)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HeaderHeight)
            .padding(horizontal = SidePadding)
            .semantics(mergeDescendants = true) { contentDescription = progress },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
            repeat(state.stepCount) { index ->
                val width by animateDpAsState(if (index == state.stepIndex) DotWide else DotSize, label = "dot")
                Box(
                    Modifier
                        .height(DotSize)
                        .width(width)
                        .clip(CircleShape)
                        .background(
                            if (index <= state.stepIndex) ItmoTheme.colorScheme.primary else ItmoTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(progress, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelLarge)
    }
}

@Composable
private fun StepBody(
    title: String,
    line: String? = null,
    top: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(bottom = ItmoTheme.spacing.group),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        top?.invoke()
        Text(
            text(title),
            Modifier.padding(horizontal = ItmoTheme.spacing.compact).padding(top = ItmoTheme.spacing.compact).semantics { heading() },
            style = ItmoTheme.emphasizedTypography.headlineLarge,
        )
        if (line != null) {
            Text(
                text(line),
                Modifier.padding(horizontal = ItmoTheme.spacing.compact).padding(bottom = ItmoTheme.spacing.content),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyLarge,
            )
        }
        content()
    }
}

/** Who signed in, from the ID token: the full name and the ISU. */
@Composable
private fun MeCard(me: OnboardingMe) {
    ListGroup(Modifier.testTag(OnboardingTestTags.ME)) {
        item {
            PersonListRow(
                title = me.name,
                pictureUrl = me.pictureUrl,
                initials = me.initials,
                subtitle = me.isu?.let { text(AccountStrings.onboarding_me_isu, it) },
            )
        }
    }
}

/** Step 1, under the person: each permission with its own button, or `разрешено` once granted. */
@Composable
private fun PermissionsStep(state: OnboardingUiState, actions: OnboardingActions) {
    ListGroup {
        item {
            PermissionRow(
                icon = Res.drawable.ic_notification,
                title = AccountStrings.onboarding_notifications_title,
                line = AccountStrings.onboarding_notifications_text,
                action = if (state.notificationsAsked) {
                    AccountStrings.onboarding_notifications_open_settings
                } else {
                    AccountStrings.onboarding_notifications_allow
                },
                granted = state.notificationsGranted,
                onAction = actions.onRequestNotifications,
                buttonTag = OnboardingTestTags.NOTIFICATIONS_BUTTON,
                grantedTag = OnboardingTestTags.NOTIFICATIONS_GRANTED,
            )
        }
        state.calendarSynced?.let { synced ->
            item {
                PermissionRow(
                    icon = Res.drawable.ic_calendar_add,
                    title = AccountStrings.onboarding_calendar_title,
                    line = AccountStrings.onboarding_calendar_text,
                    action = AccountStrings.onboarding_calendar_add,
                    granted = synced,
                    onAction = actions.onAddCalendar,
                    buttonTag = OnboardingTestTags.CALENDAR_BUTTON,
                    grantedTag = OnboardingTestTags.CALENDAR_GRANTED,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: DrawableResource,
    title: String,
    line: String,
    action: String,
    granted: Boolean,
    onAction: () -> Unit,
    buttonTag: String,
    grantedTag: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(ItmoTheme.spacing.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(painterResource(icon), if (granted) Tone.Success else Tone.Primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
            Text(text(title), style = ItmoTheme.typography.titleMedium)
            Text(text(line), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            Box(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                if (granted) {
                    StatusPill(
                        text(AccountStrings.onboarding_permission_granted),
                        Modifier.testTag(grantedTag),
                        tone = Tone.Success,
                        icon = painterResource(Res.drawable.ic_check),
                    )
                } else {
                    ProgressButton(text(action), onAction, Modifier.testTag(buttonTag), style = ProgressButtonStyle.Tonal)
                }
            }
        }
    }
}

/** Step 2: a row to read what the server keeps; once connected (a replay), the pill says so. */
@Composable
private fun ConnectionStep(state: OnboardingUiState, actions: OnboardingActions) {
    ListGroup(Modifier.testTag(OnboardingTestTags.DETAILS)) {
        item {
            ListRow(
                text(AccountStrings.onboarding_connection_details),
                icon = painterResource(Res.drawable.ic_info),
                chevron = true,
                onClick = actions.onDetails,
            )
        }
    }
    if (state.servicesEnabled) {
        StatusPill(
            text(AccountStrings.onboarding_connection_connected),
            Modifier.padding(start = ItmoTheme.spacing.compact),
            tone = Tone.Success,
            icon = painterResource(Res.drawable.ic_check),
        )
    }
}

@Composable
private fun Footer(state: OnboardingUiState, actions: OnboardingActions) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SidePadding, vertical = ItmoTheme.spacing.content),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
    ) {
        val last = state.step == OnboardingStep.CONNECTION
        ProgressButton(
            label = text(
                when {
                    !last -> AccountStrings.onboarding_next
                    state.servicesEnabled -> AccountStrings.onboarding_done
                    else -> AccountStrings.onboarding_connection_connect
                },
            ),
            onClick = if (last) actions.onConnect else actions.onNext,
            modifier = Modifier.fillMaxWidth().heightIn(min = MainButtonHeight).testTag(OnboardingTestTags.NEXT),
            inProgress = state.servicesBusy,
            enabled = !state.finished,
        )
        if (last && !state.servicesEnabled) {
            ProgressButton(
                label = text(AccountStrings.onboarding_connection_not_now),
                onClick = actions.onNotNow,
                modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.NOT_NOW),
                style = ProgressButtonStyle.Outlined,
                enabled = !state.finished && !state.servicesBusy,
            )
        }
    }
}

@Composable
private fun text(key: String, vararg arguments: Any): String = UiText.Res(key, arguments.toList()).asString()

private val HeaderHeight = 56.dp
private val SidePadding = 24.dp
private val DotSize: Dp = 8.dp
private val DotWide: Dp = 24.dp
private val MainButtonHeight = 56.dp
