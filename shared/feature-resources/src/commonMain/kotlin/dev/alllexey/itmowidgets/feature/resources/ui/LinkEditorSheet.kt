package dev.alllexey.itmowidgets.feature.resources.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.linkIcon
import dev.alllexey.itmowidgets.core.resources.title
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.gesture.tabSwipeHandover
import dev.alllexey.itmowidgets.designsystem.icons.drawable
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkAudienceOption
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkFieldError
import dev.alllexey.itmowidgets.shared.core.links_visibility_all
import dev.alllexey.itmowidgets.shared.core.links_visibility_private
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.feature.resources.Res
import dev.alllexey.itmowidgets.shared.feature.resources.links_connection_required
import dev.alllexey.itmowidgets.shared.feature.resources.links_editor_edit
import dev.alllexey.itmowidgets.shared.feature.resources.links_editor_new
import dev.alllexey.itmowidgets.shared.feature.resources.links_invalid_url
import dev.alllexey.itmowidgets.shared.feature.resources.links_name_hint
import dev.alllexey.itmowidgets.shared.feature.resources.links_save
import dev.alllexey.itmowidgets.shared.feature.resources.links_title_too_long
import dev.alllexey.itmowidgets.shared.feature.resources.links_url_clear
import dev.alllexey.itmowidgets.shared.feature.resources.links_url_hint
import dev.alllexey.itmowidgets.shared.feature.resources.links_visibility_all_review
import dev.alllexey.itmowidgets.shared.feature.resources.links_who_sees
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** What the link editor asks of its view model; closing, the clipboard and the snackbar stay with the host. */
@Immutable
class LinkEditorActions(
    val onUrlChanged: (String) -> Unit = {},
    val onCategorySelected: (LinkCategory) -> Unit = {},
    val onTitleChanged: (String) -> Unit = {},
    val onAudienceSelected: (LinkAudienceOption) -> Unit = {},
    val onSave: () -> Unit = {},
)

object LinkEditorSheetTestTags {
    const val URL = "link_editor_url"
    const val TITLE = "link_editor_title"
    const val CATEGORIES = "link_editor_categories"
    const val AUDIENCES = "link_editor_audiences"
    const val CONNECTION_HINT = "link_editor_connection_hint"
    const val SAVE = "link_editor_save"

    fun category(category: LinkCategory): String = "link_editor_category_${category.name}"
}

/**
 * The link editor over its view model. A saved link reaches [onDone] (the host closes the sheet); a failed save
 * reaches [onFailure] while the sheet is started (the host's snackbar), and the form stays as it was.
 */
@Composable
fun LinkEditorSheetRoute(
    viewModel: LinkEditorViewModel,
    onDone: () -> Unit,
    onFailure: (AppError) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onDone)
    val failure by rememberUpdatedState(onFailure)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    LinkEvent.Saved, LinkEvent.Done -> done()
                    is LinkEvent.Failed -> failure(event.error)
                }
            }
        }
    }
    val actions = remember(viewModel) {
        LinkEditorActions(
            onUrlChanged = viewModel::onUrlChanged,
            onCategorySelected = viewModel::onCategorySelected,
            onTitleChanged = viewModel::onTitleChanged,
            onAudienceSelected = viewModel::onAudienceSelected,
            onSave = viewModel::save,
        )
    }
    LinkEditorSheetContent(state, actions, modifier)
}

/**
 * Adds a link or edits an own one (`Новая ссылка` / `Изменить ссылку`): the address (up to four lines, a clear icon
 * while it has text), one chip per category, chats with the messenger they lead to, the guessed or chosen one scrolled
 * into view, the optional title hinted by the category (up to six lines), then `Кто видит`: everybody (after review
 * under premoderation), each schedule flow of the viewer named over its kind of classes, and only me. Without the
 * connection only `Только я` is offered and a line says why. Save stays pinned under the form, so the keyboard
 * never covers it; it waits for an address and a category and shows its progress while the link is saved.
 */
@Composable
fun LinkEditorSheetContent(
    state: LinkEditorUiState,
    actions: LinkEditorActions,
    modifier: Modifier = Modifier,
) {
    SheetScaffold(
        title = stringResource(if (state.editing) Res.string.links_editor_edit else Res.string.links_editor_new),
        modifier = modifier,
        footer = {
            ProgressButton(
                stringResource(Res.string.links_save),
                actions.onSave,
                Modifier
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .padding(top = ItmoTheme.spacing.compact)
                    .fillMaxWidth()
                    .testTag(LinkEditorSheetTestTags.SAVE),
                inProgress = state.saving,
                // While saving the button stays enabled to show its progress; the button itself ignores taps then.
                enabled = state.canSave || state.saving,
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = ItmoTheme.spacing.compact, bottom = ItmoTheme.spacing.compact),
        ) {
            UrlField(state, actions)
            CategoryChips(state.category, state.url, actions.onCategorySelected)
            TitleField(state, actions)
            Text(
                stringResource(Res.string.links_who_sees),
                Modifier
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .padding(top = ItmoTheme.spacing.compact)
                    .semantics { heading() },
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.labelLarge,
            )
            AudienceRows(state, actions.onAudienceSelected)
            if (state.options.size == 1) {
                Text(
                    stringResource(Res.string.links_connection_required),
                    Modifier
                        .padding(horizontal = ItmoTheme.spacing.screenMargin)
                        .padding(top = ItmoTheme.spacing.related)
                        .testTag(LinkEditorSheetTestTags.CONNECTION_HINT),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun UrlField(state: LinkEditorUiState, actions: LinkEditorActions) {
    EditorField(
        text = state.url,
        onChange = actions.onUrlChanged,
        label = stringResource(Res.string.links_url_hint),
        error = state.urlError,
        maxLines = MAX_URL_LINES,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.testTag(LinkEditorSheetTestTags.URL),
        trailingIcon = when {
            // As the View's TextInputLayout: an error takes the place of the clear icon.
            state.urlError != null -> {
                { Icon(painterResource(KitRes.drawable.ic_error), contentDescription = null) }
            }
            // Unlike the stock clear icon it stays without focus while there is text to clear.
            state.url.isNotEmpty() -> {
                {
                    IconButton(onClick = { actions.onUrlChanged("") }) {
                        Icon(
                            painterResource(KitRes.drawable.ic_close),
                            contentDescription = stringResource(Res.string.links_url_clear),
                        )
                    }
                }
            }
            else -> null
        },
    )
}

@Composable
private fun TitleField(state: LinkEditorUiState, actions: LinkEditorActions) {
    EditorField(
        text = state.title,
        onChange = actions.onTitleChanged,
        label = state.category?.title()?.asString() ?: stringResource(Res.string.links_name_hint),
        error = state.titleError,
        // 120 characters take up to six lines on a narrow screen at a large font.
        maxLines = MAX_TITLE_LINES,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.testTag(LinkEditorSheetTestTags.TITLE),
    )
}

/**
 * An outlined field that wraps instead of scrolling sideways and takes no line breaks, as the View's single-line
 * inputs did. Text set from outside (the edited link, a paste, the clear icon) puts the cursor at its end.
 */
@Composable
private fun EditorField(
    text: String,
    onChange: (String) -> Unit,
    label: String,
    error: LinkFieldError?,
    maxLines: Int,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    var edited by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val shown = if (edited.text == text) edited else TextFieldValue(text, TextRange(text.length))
    OutlinedTextField(
        value = shown,
        onValueChange = { next ->
            // A replacement of the same length keeps the selection valid.
            val single = next.copy(text = next.text.replace(LINE_BREAK, " "))
            edited = single
            if (single.text != text) onChange(single.text)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.compact),
        label = { Text(label) },
        trailingIcon = trailingIcon,
        supportingText = error?.let { { Text(stringResource(it.message())) } },
        isError = error != null,
        keyboardOptions = keyboardOptions,
        singleLine = false,
        maxLines = maxLines,
    )
}

private fun LinkFieldError.message() = when (this) {
    LinkFieldError.URL_NOT_HTTPS -> Res.string.links_invalid_url
    LinkFieldError.TITLE_TOO_LONG -> Res.string.links_title_too_long
}

/**
 * One chip per category in declaration order; a category picked by the user or guessed from the site scrolls into view.
 * Nine options and none picked on a new link, so they stay filter chips and not a button group.
 */
@Composable
private fun CategoryChips(selected: LinkCategory?, url: String, onSelect: (LinkCategory) -> Unit) {
    val list = rememberLazyListState()
    var shown by remember { mutableStateOf<LinkCategory?>(null) }
    LaunchedEffect(selected) {
        val index = selected?.ordinal ?: return@LaunchedEffect
        // The category the sheet opens with is already in place; a later one slides in.
        if (shown == null) list.scrollToItem(index) else list.animateScrollToItem(index)
        shown = selected
    }
    LazyRow(
        Modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.compact)
            // The kit's rule for every chip row; inside a sheet there is no tab swipe to hand over to.
            .tabSwipeHandover(list)
            .testTag(LinkEditorSheetTestTags.CATEGORIES),
        state = list,
        contentPadding = PaddingValues(horizontal = ItmoTheme.spacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        itemsIndexed(LinkCategory.entries, key = { _, category -> category.name }) { _, category ->
            val icon = linkIcon(category, url)
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(category.title().asString()) },
                modifier = Modifier.testTag(LinkEditorSheetTestTags.category(category)),
                leadingIcon = {
                    Icon(
                        painterResource(icon.drawable),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                // The XML chips tinted their symbol onSurfaceVariant whether checked or not.
                colors = FilterChipDefaults.filterChipColors(
                    iconColor = ItmoTheme.colorScheme.onSurfaceVariant,
                    selectedLeadingIconColor = ItmoTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * `Кто видит`: the whole row is the target, at least a touch target high; a second line in bodyMedium. The flow's
 * schedule name and the second lines do not fit a button group, so the choice stays a radio list.
 */
@Composable
private fun AudienceRows(state: LinkEditorUiState, onSelect: (LinkAudienceOption) -> Unit) {
    val selected = state.selected
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.compact)
            .padding(top = ItmoTheme.spacing.related)
            .selectableGroup()
            .testTag(LinkEditorSheetTestTags.AUDIENCES),
    ) {
        state.options.forEach { option ->
            val (title, subtitle) = audienceTexts(option, state.premoderation)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ItmoTheme.spacing.touchTarget)
                    .selectable(option == selected, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = ItmoTheme.spacing.related, vertical = ItmoTheme.spacing.related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Column(Modifier.padding(start = ItmoTheme.spacing.content)) {
                    Text(title, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
                    if (subtitle != null) {
                        Text(subtitle, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** A flow is its schedule name over the kind of classes; `Все` notes the review when there is one. */
@Composable
private fun audienceTexts(option: LinkAudienceOption, premoderation: Boolean): Pair<String, String?> = when (option) {
    LinkAudienceOption.Private -> stringResource(CoreRes.string.links_visibility_private) to null
    is LinkAudienceOption.Flow -> option.audience.label to stringResource(lessonTypeName(option.audience.typeId))
    LinkAudienceOption.All -> stringResource(CoreRes.string.links_visibility_all) to
        if (premoderation) stringResource(Res.string.links_visibility_all_review) else null
}

private val LINE_BREAK = Regex("[\r\n]")
private const val MAX_URL_LINES = 4
private const val MAX_TITLE_LINES = 6
