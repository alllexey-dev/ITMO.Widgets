package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.shared.core.CoreStrings

/**
 * What a settings page asks its host to do: leave, change a row, open another page, run a row's own command
 * ([onAction]) or the button of a host dialog. A choice arrives as the picked option.
 */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onToggle: (id: SettingRowId, checked: Boolean) -> Unit = { _, _ -> },
    val onChoice: (id: SettingRowId, optionKey: String) -> Unit = { _, _ -> },
    val onNavigate: (page: SettingsPage) -> Unit = {},
    val onAction: (id: SettingRowId) -> Unit = {},
    /** «Разрешить» of the calendar rationale: the system permission dialog. */
    val onAllowCalendarAccess: () -> Unit = {},
    /** «Открыть настройки» of the locked calendar rationale: the app's system page. */
    val onOpenAppSettings: () -> Unit = {},
)

/** Tags for host and instrumented tests. Every row is tagged with its `SettingRowId.key`. */
object SettingsTestTags {
    const val SCROLL = "settings_scroll"
}

/**
 * One settings page as a pushed screen of the kit, like «Уведомления» and «Сервер ITMO.Widgets»: the page title in
 * the detail header, then its [SettingSectionView]s; an untitled group after another keeps a group's gap. Stateless:
 * a switch shows what [state] says, so a refused, cancelled or failed change never needs undoing on screen. The
 * page's one dialog is the host's calendar rationale in [dialogs].
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    dialogs: SettingsDialogState = rememberSettingsDialogState(),
) {
    ItmoDetailScreen(
        title = state.page.title.asString(),
        onBack = actions.onBack,
        backLabel = UiText.Res(CoreStrings.settings_back).asString(),
        modifier = modifier,
    ) { padding ->
        // The scroll column appears only with rows, so a restored position is not clamped to an empty page.
        if (state.sections.isEmpty()) return@ItmoDetailScreen
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(SettingsTestTags.SCROLL)
                .verticalScroll(scrollState)
                .padding(horizontal = ItmoTheme.spacing.screenMargin)
                .padding(top = ItmoTheme.spacing.related, bottom = ItmoTheme.spacing.group)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            state.sections.forEachIndexed { index, section ->
                if (index > 0 && section.title == null) Spacer(Modifier.height(ItmoTheme.spacing.compact))
                SettingSectionView(section, actions)
            }
        }
    }
    SettingsDialogs(dialogs, actions)
}
