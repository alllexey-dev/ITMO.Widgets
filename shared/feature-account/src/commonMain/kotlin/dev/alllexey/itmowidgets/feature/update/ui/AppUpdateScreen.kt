package dev.alllexey.itmowidgets.feature.update.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarAction
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateUiState
import dev.alllexey.itmowidgets.shared.core.common_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_download
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.app_update_action
import dev.alllexey.itmowidgets.shared.feature.account.app_update_description
import dev.alllexey.itmowidgets.shared.feature.account.app_update_later
import dev.alllexey.itmowidgets.shared.feature.account.app_update_skip
import dev.alllexey.itmowidgets.shared.feature.account.app_update_title
import dev.alllexey.itmowidgets.shared.feature.account.app_update_unsupported_description
import dev.alllexey.itmowidgets.shared.feature.account.app_update_unsupported_title
import dev.alllexey.itmowidgets.shared.feature.account.app_update_versions
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Tags a test or a host looks the update offer up by; named after the view ids of the XML screen. */
object AppUpdateTestTags {
    const val CLOSE = "app_update_close"
    const val TITLE = "app_update_title"
    const val VERSIONS = "app_update_versions"
    const val DESCRIPTION = "app_update_description"
    const val UPDATE = "app_update_update"
    const val LATER = "app_update_later"
    const val SKIP = "app_update_skip"
}

/**
 * The offer of the release Backend reported: a close button, then a scrolling content-state column with the icon,
 * the title (outdated when [AppUpdateUiState.unsupported]), the versions, the reason followed by the release notes,
 * and three stacked actions. An unsupported build has nowhere to skip to, so it only offers the reminder.
 */
@Composable
fun AppUpdateScreen(
    state: AppUpdateUiState,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onClose: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            // The XML button had a fixed 48 dp box; the kit action keeps 48 dp as a minimum at any font scale.
            AppTopBarAction(
                icon = painterResource(KitRes.drawable.ic_close),
                label = stringResource(CoreRes.string.common_close),
                onClick = onClose,
                modifier = Modifier.padding(start = ItmoTheme.spacing.compact, top = ItmoTheme.spacing.related)
                    .testTag(AppUpdateTestTags.CLOSE),
            )
            // The whole offer scrolls: three stacked actions and release notes outgrow a tall font scale.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(ItmoTheme.spacing.statePadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppUpdateOffer(state, onUpdate, onLater, onSkip)
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun AppUpdateOffer(
    state: AppUpdateUiState,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    Icon(
        painterResource(KitRes.drawable.ic_download),
        contentDescription = null,
        modifier = Modifier.size(ItmoTheme.spacing.stateIcon),
        tint = ItmoTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(ItmoTheme.spacing.group))
    Text(
        stringResource(if (state.unsupported) Res.string.app_update_unsupported_title else Res.string.app_update_title),
        Modifier.testTag(AppUpdateTestTags.TITLE),
        color = ItmoTheme.colorScheme.onSurface,
        style = ItmoTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(ItmoTheme.spacing.related))
    Text(
        stringResource(Res.string.app_update_versions, state.installed, state.latest),
        Modifier.testTag(AppUpdateTestTags.VERSIONS),
        color = ItmoTheme.colorScheme.primary,
        style = ItmoTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(ItmoTheme.spacing.compact))
    Text(
        description(state),
        Modifier.testTag(AppUpdateTestTags.DESCRIPTION),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(ActionGap))
    ProgressButton(
        stringResource(Res.string.app_update_action),
        onUpdate,
        Modifier.testTag(AppUpdateTestTags.UPDATE),
        style = ProgressButtonStyle.Filled,
    )
    Spacer(Modifier.height(ItmoTheme.spacing.compact))
    ProgressButton(
        stringResource(Res.string.app_update_later),
        onLater,
        Modifier.testTag(AppUpdateTestTags.LATER),
        style = ProgressButtonStyle.Tonal,
    )
    if (!state.unsupported) {
        Spacer(Modifier.height(ItmoTheme.spacing.related))
        ProgressButton(
            stringResource(Res.string.app_update_skip),
            onSkip,
            Modifier.testTag(AppUpdateTestTags.SKIP),
            style = ProgressButtonStyle.Text,
        )
    }
}

/**
 * Release notes come from Backend and explain the release; they do not explain why this screen is open, so they
 * follow the reason after a blank line instead of replacing it.
 */
@Composable
private fun description(state: AppUpdateUiState): String {
    val reason = stringResource(
        if (state.unsupported) Res.string.app_update_unsupported_description else Res.string.app_update_description
    )
    return if (state.note.isEmpty()) reason else "$reason\n\n${state.note}"
}

/** `Widget.ItmoWidgets.ContentState.PrimaryAction`'s 20 dp top margin, off the spacing scale. */
private val ActionGap = 20.dp
