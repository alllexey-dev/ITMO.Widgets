package dev.alllexey.itmowidgets.feature.reviews.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewLimits
import dev.alllexey.itmowidgets.core.text.shortPersonName
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoSwitch
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.gesture.tabSwipeHandover
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorUiState
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewFieldError
import dev.alllexey.itmowidgets.shared.feature.reviews.Res
import dev.alllexey.itmowidgets.shared.feature.reviews.review_anonymous
import dev.alllexey.itmowidgets.shared.feature.reviews.review_anonymous_off
import dev.alllexey.itmowidgets.shared.feature.reviews.review_discard
import dev.alllexey.itmowidgets.shared.feature.reviews.review_discard_title
import dev.alllexey.itmowidgets.shared.feature.reviews.review_editor_close
import dev.alllexey.itmowidgets.shared.feature.reviews.review_editor_edit
import dev.alllexey.itmowidgets.shared.feature.reviews.review_editor_new
import dev.alllexey.itmowidgets.shared.feature.reviews.review_save
import dev.alllexey.itmowidgets.shared.feature.reviews.review_send
import dev.alllexey.itmowidgets.shared.feature.reviews.review_subject_hint
import dev.alllexey.itmowidgets.shared.feature.reviews.review_subject_too_long
import dev.alllexey.itmowidgets.shared.feature.reviews.review_text_hint
import dev.alllexey.itmowidgets.shared.feature.reviews.review_text_too_long
import dev.alllexey.itmowidgets.shared.feature.reviews.review_text_too_short
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_check

/**
 * What the review editor asks of its host: the field changes and the send go to `ReviewEditorViewModel`; [onClose]
 * is the close button (the host decides whether to ask first, as for back); [onDiscard] and [onKeepEditing] answer
 * the question `Не сохранять отзыв?`.
 */
@Immutable
class ReviewEditorActions(
    val onSubjectChange: (String) -> Unit = {},
    val onTextChange: (String) -> Unit = {},
    val onAnonymousChange: (Boolean) -> Unit = {},
    val onSave: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onKeepEditing: () -> Unit = {},
)

/**
 * What [ReviewEditorSheetContent] draws: the form of `ReviewEditorViewModel`, the teacher's full name and the two
 * text fields with their cursors.
 */
@Immutable
class ReviewEditorSheetState(
    val form: ReviewEditorUiState,
    val teacherName: String,
    val subject: TextFieldValue = TextFieldValue(form.subject, TextRange(form.subject.length)),
    val text: TextFieldValue = TextFieldValue(form.text, TextRange(form.text.length)),
)

/** Tags of the editor's parts for host tests. */
object ReviewEditorSheetTestTags {
    const val SCROLL = "review_editor_scroll"
    const val SUBJECT = "review_editor_subject"
    const val SUGGESTIONS = "review_editor_suggestions"
    const val TEXT = "review_editor_text"
    const val ANONYMOUS = "review_editor_anonymous"
    const val SEND = "review_editor_send"
}

/**
 * The review editor the Fragment host calls with the view model's [form]. The two fields start from the form, so text
 * restored from the view model's `SavedStateHandle` after recreation or process death comes back with the cursor at
 * its end; from then on every edit goes to the view model, which never changes a field on its own, so the fields are
 * not saved a second time here. A suggestion chip fills the subject (or clears it when it is the picked one) and puts
 * the cursor at the end. While [discarding], the question `Не сохранять отзыв?` stands over the sheet.
 */
@Composable
fun ReviewEditorSheet(
    form: ReviewEditorUiState,
    teacherName: String,
    discarding: Boolean,
    actions: ReviewEditorActions,
    modifier: Modifier = Modifier,
) {
    var subject by remember { mutableStateOf(endCursor(form.subject)) }
    var text by remember { mutableStateOf(endCursor(form.text)) }
    val fields = ReviewEditorActions(
        // A suggestion chip: the picked subject (or none) with the cursor at its end.
        onSubjectChange = { value ->
            subject = endCursor(value)
            actions.onSubjectChange(value)
        },
        onTextChange = actions.onTextChange,
        onAnonymousChange = actions.onAnonymousChange,
        onSave = actions.onSave,
        onClose = actions.onClose,
        onDiscard = actions.onDiscard,
        onKeepEditing = actions.onKeepEditing,
    )
    ReviewEditorSheetContent(
        ReviewEditorSheetState(form, teacherName, subject, text),
        fields,
        modifier,
        // A moved cursor alone is no edit; the form may lag a frame behind the field, so compare with the field.
        onSubjectEdit = { field ->
            val changed = field.text != subject.text
            subject = field
            if (changed) actions.onSubjectChange(field.text)
        },
        onTextEdit = { field ->
            val changed = field.text != text.text
            text = field
            if (changed) actions.onTextChange(field.text)
        },
    )
    if (discarding) {
        ConfirmDialog(
            title = stringResource(Res.string.review_discard_title),
            confirmLabel = stringResource(Res.string.review_discard),
            dismissLabel = stringResource(CoreRes.string.common_cancel),
            onConfirm = actions.onDiscard,
            onDismiss = actions.onKeepEditing,
        )
    }
}

/**
 * The body of the review editor (`sheet_review_editor.xml`): the title over the teacher's short name with the close
 * button, the optional subject (up to three lines) with the suggestion chips under it, the text with its counter to
 * [TeacherReviewLimits.MAX_TEXT] and the minimum as a hint that turns into the error on send, `Анонимно` with its
 * warning when off, and `Отправить` or `Сохранить` pinned under the scrolling form, so the keyboard never hides it.
 * Stateless: the field edits arrive as [ReviewEditorActions.onSubjectChange] and [ReviewEditorActions.onTextChange]
 * with plain text, or, with the cursor, through [onSubjectEdit] and [onTextEdit] when the caller keeps the fields.
 * The host owns the sheet's container and the form mode (no handle, no drag, no tap outside).
 */
@Composable
fun ReviewEditorSheetContent(
    state: ReviewEditorSheetState,
    actions: ReviewEditorActions,
    modifier: Modifier = Modifier,
    onSubjectEdit: (TextFieldValue) -> Unit = { actions.onSubjectChange(it.text) },
    onTextEdit: (TextFieldValue) -> Unit = { actions.onTextChange(it.text) },
) {
    val form = state.form
    SheetScaffold(
        title = stringResource(if (form.editing) Res.string.review_editor_edit else Res.string.review_editor_new),
        modifier = modifier,
        subtitle = shortPersonName(state.teacherName),
        handle = false,
        close = SheetClose(stringResource(Res.string.review_editor_close), actions.onClose),
        footer = {
            ProgressButton(
                label = stringResource(if (form.editing) Res.string.review_save else Res.string.review_send),
                onClick = actions.onSave,
                modifier = Modifier
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .padding(top = ItmoTheme.spacing.group)
                    .fillMaxWidth()
                    .testTag(ReviewEditorSheetTestTags.SEND),
                inProgress = form.saving,
                enabled = form.text.isNotBlank(),
            )
        },
    ) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .testTag(ReviewEditorSheetTestTags.SCROLL)
                .verticalScroll(rememberScrollState()),
        ) {
            SubjectField(state.subject, form.subjectError, onSubjectEdit)
            if (form.suggestions.isNotEmpty()) {
                Suggestions(form.suggestions, form.subject, actions.onSubjectChange)
            }
            TextField(state.text, form, onTextEdit)
            AnonymousRow(form.anonymous, actions.onAnonymousChange)
            if (!form.anonymous) {
                Text(
                    stringResource(Res.string.review_anonymous_off),
                    Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SubjectField(value: TextFieldValue, error: ReviewFieldError?, onEdit: (TextFieldValue) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .testTag(ReviewEditorSheetTestTags.SUBJECT),
        label = { Text(stringResource(Res.string.review_subject_hint)) },
        isError = error != null,
        supportingText = error?.let { { Text(fieldErrorText(it)) } },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next,
        ),
        // A long subject wraps instead of scrolling away, and the keyboard still moves on to the text.
        maxLines = MAX_SUBJECT_LINES,
    )
}

/**
 * Subjects of the viewer's own lessons with the teacher; the chip of the typed subject shows as picked. A picked chip
 * clears again and the subject may be typed freely, so these stay filter chips and not a button group.
 */
@Composable
private fun Suggestions(suggestions: List<String>, current: String, onPick: (String) -> Unit) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.related)
            .testTag(ReviewEditorSheetTestTags.SUGGESTIONS)
            // Inside a sheet the handover changes nothing; every horizontal scroller carries one (GestureRulesTest).
            .tabSwipeHandover(scroll)
            .horizontalScroll(scroll)
            .padding(horizontal = ItmoTheme.spacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        suggestions.forEach { name ->
            val picked = name == current
            FilterChip(
                selected = picked,
                onClick = { onPick(if (picked) "" else name) },
                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.widthIn(max = ChipMaxWidth),
                leadingIcon = if (picked) {
                    {
                        Icon(
                            painterResource(KitRes.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun TextField(value: TextFieldValue, form: ReviewEditorUiState, onEdit: (TextFieldValue) -> Unit) {
    val error = form.textError
    OutlinedTextField(
        value = value,
        onValueChange = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.compact)
            .testTag(ReviewEditorSheetTestTags.TEXT),
        label = { Text(stringResource(Res.string.review_text_hint)) },
        isError = error != null,
        supportingText = {
            Row {
                val note = when {
                    error != null -> fieldErrorText(error)
                    form.showsMinimumHint ->
                        stringResource(Res.string.review_text_too_short, TeacherReviewLimits.MIN_TEXT)
                    else -> ""
                }
                Text(note, Modifier.weight(1f))
                Text("${form.text.length}/${TeacherReviewLimits.MAX_TEXT}")
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = MIN_TEXT_LINES,
    )
}

@Composable
private fun AnonymousRow(anonymous: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.compact)
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .toggleable(anonymous, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .testTag(ReviewEditorSheetTestTags.ANONYMOUS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.review_anonymous),
            Modifier.weight(1f),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodyLarge,
        )
        ItmoSwitch(checked = anonymous, onCheckedChange = null)
    }
}

@Composable
private fun fieldErrorText(error: ReviewFieldError): String = when (error) {
    ReviewFieldError.TEXT_TOO_SHORT -> stringResource(Res.string.review_text_too_short, TeacherReviewLimits.MIN_TEXT)
    ReviewFieldError.TEXT_TOO_LONG -> stringResource(Res.string.review_text_too_long, TeacherReviewLimits.MAX_TEXT)
    ReviewFieldError.SUBJECT_TOO_LONG ->
        stringResource(Res.string.review_subject_too_long, TeacherReviewLimits.MAX_SUBJECT)
}

private fun endCursor(text: String) = TextFieldValue(text, TextRange(text.length))

/** `item_review_subject_chip.xml`'s `maxWidth`. */
private val ChipMaxWidth = 280.dp

private const val MAX_SUBJECT_LINES = 3
private const val MIN_TEXT_LINES = 5

/*
 * Previews. The harness names a baseline `<function>_<@Preview name>`, and LX-1c recorded the XML references as
 * `ReviewEditorSheetContent_<state>`, so each state is a function called `ReviewEditorSheetContent` in a holder class
 * of its own. The sheet wraps its content on `surfaceContainerLow`, as `ReviewEditorBottomSheet` opens it.
 */

/** A new review once the schedule history answered: three suggestions, nothing typed. */
internal class ReviewEditorSheetNewPreview {
    @Preview(name = "new")
    @Composable
    fun ReviewEditorSheetContent() = EditorPreview(
        ReviewEditorUiState(suggestions = listOf("Математический анализ", "Линейная алгебра", "Дискретная математика")),
        "Константинопольская Александра Константиновна",
    )
}

/** The own review opened again: subject, text, the name shown, `Сохранить`. */
internal class ReviewEditorSheetEditPreview {
    @Preview(name = "edit")
    @Composable
    fun ReviewEditorSheetContent() = EditorPreview(
        ReviewEditorUiState(
            subject = "Математический анализ",
            text = "Лекции понятные, на практике разбираем задачи из контрольных.",
            anonymous = false,
            editing = true,
        ),
        "Константинопольская Александра Константиновна",
    )
}

/** 29 characters sent: the minimum is the text's error now. */
internal class ReviewEditorSheetTooShortPreview {
    @Preview(name = "too-short")
    @Composable
    fun ReviewEditorSheetContent() = EditorPreview(
        ReviewEditorUiState(
            text = "а".repeat(TeacherReviewLimits.MIN_TEXT - 1),
            textError = ReviewFieldError.TEXT_TOO_SHORT,
        ),
        "Константинопольская Александра Константиновна",
    )
}

/** Anonymity turned off: the warning under the switch. */
internal class ReviewEditorSheetNamedPreview {
    @Preview(name = "named")
    @Composable
    fun ReviewEditorSheetContent() = EditorPreview(
        ReviewEditorUiState(anonymous = false),
        "Константинопольская Александра Константиновна",
    )
}

/** A subject over three lines and a suggestion wider than a chip may grow. */
internal class ReviewEditorSheetLongSubjectPreview {
    @Preview(name = "long-subject")
    @Composable
    fun ReviewEditorSheetContent() = EditorPreview(
        ReviewEditorUiState(
            subject = "Проектирование и разработка распределённых информационных систем реального времени " +
                "с элементами машинного обучения и анализа больших данных (продвинутый курс)",
            suggestions = listOf(
                "Проектирование и разработка распределённых информационных систем реального времени",
                "Базы данных",
            ),
        ),
        "Константинопольская Александра Константиновна",
    )
}

/** Sent and waiting for Backend: the button shows the progress. */
internal class ReviewEditorSheetSavingPreview {
    @Preview(name = "saving")
    @Composable
    fun ReviewEditorSheetContent() = EditorPreview(
        ReviewEditorUiState(
            subject = "Линейная алгебра",
            text = "Объясняет подробно, на вопросы после пары всегда отвечает.",
            saving = true,
        ),
        "Константинопольская Александра Константиновна",
    )
}

@Composable
private fun EditorPreview(form: ReviewEditorUiState, teacherName: String) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) {
        ReviewEditorSheetContent(ReviewEditorSheetState(form, teacherName), ReviewEditorActions())
    }
}
