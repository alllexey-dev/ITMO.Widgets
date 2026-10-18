package dev.alllexey.itmowidgets.feature.settings.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SegmentedChoice
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.WidgetCard
import dev.alllexey.itmowidgets.feature.settings.presentation.WidgetCardAction
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsActions
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_tune
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import org.jetbrains.compose.resources.painterResource

/** Tags for the gallery's screen and host tests: a card and its parts by the format's name. */
object WidgetsGalleryTags {
    const val SCROLL = "widgets_gallery"
    const val SHEET = "widgets_sheet"
    fun card(format: WidgetFormat) = "widget_card_${format.name.lowercase()}"
    fun add(format: WidgetFormat) = "widget_add_${format.name.lowercase()}"
    fun settings(format: WidgetFormat) = "widget_settings_${format.name.lowercase()}"
    fun beta(format: WidgetFormat) = "widget_beta_${format.name.lowercase()}"
}

/** `Добавить` pins a widget of a format through the launcher. */
data class WidgetsGalleryActions(val onAdd: (WidgetFormat) -> Unit = {})

/**
 * `Виджеты` (spec C4): a card per format with the real widget drawn by the host through [widgetPreview] on a
 * wallpaper, `Добавить` or `на экране`, the format's settings in a sheet with the preview again (applied at once to
 * every widget of the format), then the gallery's own rows. Stateless but for which sheet is open.
 */
@Composable
fun WidgetsGalleryScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    galleryActions: WidgetsGalleryActions,
    widgetPreview: @Composable (WidgetPreviewSettings, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    initialSheet: WidgetFormat? = null,
) {
    val gallery = state.gallery ?: return
    var sheet by rememberSaveable { mutableStateOf(initialSheet) }
    ItmoDetailScreen(
        title = state.page.title.asString(),
        subtitle = gallery.subtitle?.asString(),
        onBack = actions.onBack,
        backLabel = UiText.Res(CoreStrings.settings_back).asString(),
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .testTag(WidgetsGalleryTags.SCROLL)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ItmoTheme.spacing.screenMargin)
                .padding(bottom = ItmoTheme.spacing.group)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            val (wide, narrow) = gallery.cards.partition { it.format in WIDE }
            wide.forEach { card -> GalleryCard(card, galleryActions, widgetPreview, onSettings = { sheet = card.format }, wide = true) }
            Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
                narrow.forEach { card ->
                    GalleryCard(card, galleryActions, widgetPreview, onSettings = { sheet = card.format }, wide = false, Modifier.weight(1f))
                }
            }
            state.sections.forEach { section -> Section(section, actions) }
        }
    }
    gallery.cards.firstOrNull { it.format == sheet }?.let { card ->
        WidgetSettingsSheet(card, actions, widgetPreview, onDismiss = { sheet = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryCard(
    card: WidgetCard,
    actions: WidgetsGalleryActions,
    widgetPreview: @Composable (WidgetPreviewSettings, Modifier) -> Unit,
    onSettings: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    ItmoCard(modifier.testTag(WidgetsGalleryTags.card(card.format))) {
        Wallpaper { widgetPreview(card.preview, Modifier.fillMaxWidth()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = ItmoTheme.spacing.related)) {
                // The pill wraps under a long title rather than squeezing it.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(card.title.asString(), style = ItmoTheme.typography.titleMedium, color = ItmoTheme.colorScheme.onSurface)
                    if (card.beta) {
                        StatusPill(
                            UiText.Res(SettingsStrings.settings_widget_beta).asString(),
                            Modifier.testTag(WidgetsGalleryTags.beta(card.format)),
                            tone = Tone.Tertiary,
                        )
                    }
                }
                Text(card.description.asString(), style = ItmoTheme.typography.bodySmall, color = ItmoTheme.colorScheme.onSurfaceVariant)
            }
            if (wide) CardAction(card, actions)
            if (card.settings.isNotEmpty()) {
                val label = UiText.Res(SettingsStrings.settings_widget_settings).asString()
                IconButton(onClick = onSettings, modifier = Modifier.testTag(WidgetsGalleryTags.settings(card.format)).semantics { contentDescription = label }) {
                    Icon(painterResource(Res.drawable.ic_tune), contentDescription = null, tint = ItmoTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!wide) CardAction(card, actions)
    }
}

@Composable
private fun CardAction(card: WidgetCard, actions: WidgetsGalleryActions) {
    when (card.action) {
        WidgetCardAction.ON_SCREEN -> StatusPill(
            UiText.Res(SettingsStrings.settings_widget_on_screen).asString(),
            tone = Tone.Success,
            icon = painterResource(Res.drawable.ic_check),
        )
        WidgetCardAction.ADD -> FilledTonalButton(
            onClick = { actions.onAdd(card.format) },
            contentPadding = PaddingValues(horizontal = ItmoTheme.spacing.content),
            modifier = Modifier.testTag(WidgetsGalleryTags.add(card.format)),
        ) {
            Icon(painterResource(Res.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(ItmoTheme.spacing.related))
            Text(UiText.Res(SettingsStrings.settings_widget_add).asString())
        }
        WidgetCardAction.NONE -> Unit
    }
}

/** The home screen behind a preview: the scheme's containers, so a widget reads as placed on a wallpaper. */
@Composable
private fun Wallpaper(content: @Composable () -> Unit) {
    val scheme = ItmoTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(ItmoTheme.shapes.cardContent)
            .background(Brush.linearGradient(listOf(scheme.tertiaryContainer, scheme.primaryContainer, scheme.secondaryContainer)))
            .padding(ItmoTheme.spacing.content),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetSettingsSheet(
    card: WidgetCard,
    actions: SettingsActions,
    widgetPreview: @Composable (WidgetPreviewSettings, Modifier) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = ItmoTheme.colorScheme.surfaceContainerLow,
    ) {
        WidgetSettingsSheetContent(card, actions, widgetPreview, onDismiss)
    }
}

/** The sheet of one format: the preview, the note, the rows; `Размер текста` as a segmented choice. */
@Composable
fun WidgetSettingsSheetContent(
    card: WidgetCard,
    actions: SettingsActions,
    widgetPreview: @Composable (WidgetPreviewSettings, Modifier) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetScaffold(
        title = card.title.asString(),
        modifier = modifier.testTag(WidgetsGalleryTags.SHEET),
        close = SheetClose(UiText.Res(CoreStrings.common_close).asString(), onDismiss),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ItmoTheme.spacing.section).padding(bottom = ItmoTheme.spacing.group),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Wallpaper { widgetPreview(card.preview, Modifier.fillMaxWidth()) }
            card.settingsNote?.let { ListGroupNote(it.asString()) }
            card.settings.forEach { section -> Section(section, actions) }
        }
    }
}

@Composable
private fun ColumnScope.Section(section: SettingSection, actions: SettingsActions) {
    val choice = section.items.singleOrNull() as? SettingItem.Choice
    section.title?.let { SectionHeader(it.asString()) }
    if (choice != null && choice.options.size <= SEGMENTS) {
        val keys = choice.options.map { it.key }
        SegmentedChoice(
            options = choice.options.map { it.label.asString() },
            selectedIndex = keys.indexOf(choice.selectedOptionKey),
            onSelect = { actions.onChoice(choice.id, keys[it]) },
            enabled = choice.enabled,
            testTags = keys.map { "${choice.id.key}_$it" },
        )
    } else {
        ListGroup {
            section.items.forEach { item -> item { SettingRow(item, actions) } }
        }
    }
    section.footer?.let { ListGroupNote(it.asString()) }
}

@Composable
private fun SettingRow(item: SettingItem, actions: SettingsActions) {
    val tag = Modifier.testTag(item.id.key)
    when (item) {
        is SettingItem.Toggle -> SwitchListRow(
            title = item.title.asString(),
            checked = item.checked,
            onCheckedChange = { actions.onToggle(item.id, it) },
            modifier = tag,
            subtitle = item.description?.asString(),
            enabled = item.enabled,
        )
        is SettingItem.Choice -> ChoiceRow(item, actions, tag)
        is SettingItem.Action -> ListRow(
            title = item.title.asString(),
            modifier = tag,
            subtitle = item.description?.asString(),
            trailing = item.value?.asString(),
            onClick = if (item.enabled) ({ actions.onAction(item.id) }) else null,
        )
        else -> Unit
    }
}

/** A choice with more options than a segmented control holds: its options as a list under the title. */
@Composable
private fun ChoiceRow(item: SettingItem.Choice, actions: SettingsActions, modifier: Modifier) {
    ListRow(
        title = item.title.asString(),
        modifier = modifier,
        below = {
            item.options.forEach { option ->
                ListRow(
                    title = option.label.asString(),
                    accessory = if (option.key == item.selectedOptionKey) {
                        { Icon(painterResource(Res.drawable.ic_check), contentDescription = null, tint = ItmoTheme.colorScheme.primary) }
                    } else null,
                    onClick = if (item.enabled) ({ actions.onChoice(item.id, option.key) }) else null,
                )
            }
        },
    )
}

private const val SEGMENTS = 4

/** The formats drawn across the screen; the others share a row. */
private val WIDE = setOf(WidgetFormat.NEXT, WidgetFormat.DAY, WidgetFormat.LIST)
