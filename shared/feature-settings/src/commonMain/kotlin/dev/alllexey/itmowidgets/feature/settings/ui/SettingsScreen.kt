package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarBack
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoLoadingIndicator
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsActionRow
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsChoiceRow
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsGroup
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsGroupFooter
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsInfoRow
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsNavigationRow
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsToggleRow
import dev.alllexey.itmowidgets.designsystem.icons.drawable
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.settings_back
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_loading
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a settings page asks its host to do: leave, change a row, open another page, run a row's own command
 * ([onAction]) or the button of a host dialog. The screen opens its dialogs itself, so a choice arrives as the picked
 * option and turning the custom services on arrives only after the consent.
 */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onToggle: (id: SettingRowId, checked: Boolean) -> Unit = { _, _ -> },
    val onChoice: (id: SettingRowId, optionKey: String) -> Unit = { _, _ -> },
    val onNavigate: (page: SettingsPage) -> Unit = {},
    val onAction: (id: SettingRowId) -> Unit = {},
    /** «Разрешить» of the background work hint: the system page that lifts the restriction. */
    val onAllowBackgroundWork: () -> Unit = {},
    /** «Разрешить» of the calendar rationale: the system permission dialog. */
    val onAllowCalendarAccess: () -> Unit = {},
    /** «Открыть настройки» of the locked calendar rationale: the app's system page. */
    val onOpenAppSettings: () -> Unit = {},
)

/**
 * Tags named after the View ids of `fragment_settings.xml`, for host and instrumented tests. Every row is tagged
 * with its `SettingRowId.key`.
 */
object SettingsTestTags {
    const val BACK = "back_button"
    const val WIDGET_PREVIEW = "widget_preview_container"
    const val SCROLL = "settings_scroll"
    const val PROGRESS = "settings_progress"
    const val FOOTER = "setting_section_footer"
}

/**
 * One settings page: the top bar with the page title, the widget the page configures (drawn by the host through
 * [widgetPreview], above the scrolling rows and never scrolled), and the page's [SettingSection]s as compact settings
 * groups. Only privacy shows a progress while Backend has not answered; every other page enters with its rows.
 * Stateless: a switch shows what [state] says, so a refused, cancelled or failed change never needs undoing on
 * screen. The page's one dialog is [dialogs]: a choice row and turning the custom services on open theirs, the host
 * opens the background work and calendar ones.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    widgetPreview: @Composable (WidgetPreviewSettings) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    dialogs: SettingsDialogState = rememberSettingsDialogState(),
) {
    Column(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        AppTopBar(
            title = state.page.title.asString(),
            navigation = {
                AppTopBarBack(
                    stringResource(CoreRes.string.settings_back),
                    actions.onBack,
                    Modifier.testTag(SettingsTestTags.BACK),
                )
            },
        )
        state.previewSettings?.let { settings ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .padding(bottom = ItmoTheme.spacing.compact)
                    .testTag(SettingsTestTags.WIDGET_PREVIEW),
            ) { widgetPreview(settings) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                // The scroll column appears only with rows, so a restored position is not clamped to an empty page.
                state.sections.isNotEmpty() -> SettingsSections(state.sections, actions, dialogs, scrollState)
                state.page == SettingsPage.PRIVACY && state.loaded -> PrivacyProgress(Modifier.align(Alignment.Center))
            }
        }
    }
    SettingsDialogs(dialogs, state.sections, actions)
}

@Composable
private fun SettingsSections(
    sections: List<SettingSection>,
    actions: SettingsActions,
    dialogs: SettingsDialogState,
    scrollState: ScrollState,
) {
    Column(
        Modifier
            .fillMaxSize()
            .testTag(SettingsTestTags.SCROLL)
            .verticalScroll(scrollState)
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(bottom = ItmoTheme.spacing.group)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
    ) {
        sections.forEachIndexed { index, section ->
            val top = section.topSpacing(first = index == 0)
            SettingsGroup(
                modifier = if (top == null) Modifier else Modifier.padding(top = top),
                title = section.title?.asString(),
                footer = section.footer?.let { footer ->
                    { SettingsGroupFooter(footer.asString(), Modifier.testTag(SettingsTestTags.FOOTER)) }
                },
            ) {
                section.items.forEach { item -> row(item.id) { SettingRow(item, actions, dialogs) } }
            }
        }
    }
}

/**
 * The gap above a group: a titled group keeps its label's margin (8 dp on the first one), an untitled group after
 * another keeps the group gap itself, and an untitled first group starts right under the bar or the preview.
 */
@Composable
private fun SettingSection.topSpacing(first: Boolean): Dp? = when {
    title != null && first -> ItmoTheme.spacing.compact
    title != null || !first -> ItmoTheme.spacing.group
    else -> null
}

@Composable
private fun SettingRow(item: SettingItem, actions: SettingsActions, dialogs: SettingsDialogState) {
    val tag = Modifier.testTag(item.id.key)
    when (item) {
        is SettingItem.Toggle -> SettingsToggleRow(
            title = item.title.asString(),
            checked = item.checked.takeIf { item.stateKnown },
            onCheckedChange = { checked ->
                // The services reach Backend, so they are turned on only after the consent; off needs none.
                if (item.id == SettingRowId.CUSTOM_SERVICES && checked) {
                    dialogs.show(SettingsDialog.CustomServicesConsent)
                } else {
                    actions.onToggle(item.id, checked)
                }
            },
            modifier = tag,
            description = item.description?.asString(),
            enabled = item.enabled,
        )
        is SettingItem.Choice -> SettingsChoiceRow(
            title = item.title.asString(),
            value = item.value.asString(),
            onClick = { dialogs.show(SettingsDialog.Choice(item.id)) },
            modifier = tag,
            description = item.description?.asString(),
            enabled = item.enabled,
        )
        is SettingItem.Navigation -> SettingsNavigationRow(
            title = item.title.asString(),
            onClick = { actions.onNavigate(item.page) },
            modifier = tag,
            description = item.description?.asString(),
            value = item.value?.asString(),
            enabled = item.enabled,
        )
        is SettingItem.Action -> SettingsActionRow(
            title = item.title.asString(),
            onClick = { actions.onAction(item.id) },
            modifier = tag,
            description = item.description?.asString(),
            value = item.value?.asString(),
            trailingIcon = item.trailingIcon?.let { icon -> painterResource(icon.drawable) },
            enabled = item.enabled,
        )
        is SettingItem.Info -> SettingsInfoRow(item.title.asString(), item.value.asString(), tag)
    }
}

/**
 * `settings_progress`: the one loading state of settings, read as `settings_loading`. Backend answers within a few
 * seconds, so it is the kit's short-wait indicator (the large spinner under the iOS style).
 */
@Composable
private fun PrivacyProgress(modifier: Modifier) {
    val description = stringResource(Res.string.settings_loading)
    ItmoLoadingIndicator(
        modifier
            .testTag(SettingsTestTags.PROGRESS)
            .semantics { contentDescription = description },
    )
}
