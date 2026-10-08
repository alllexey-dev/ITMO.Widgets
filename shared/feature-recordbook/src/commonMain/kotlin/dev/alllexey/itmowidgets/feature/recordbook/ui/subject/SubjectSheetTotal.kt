package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenu
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookGradeScale
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.KeyKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetHeaders
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SheetLinkOption
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetState
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.labelResource
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.sheetColumnTitle
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.sheetScoreCaption
import dev.alllexey.itmowidgets.shared.designsystem.ic_more_vert
import dev.alllexey.itmowidgets.shared.designsystem.ic_table
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_score_pending
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_actions
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_change_total
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_disconnect
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_hint
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_open
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_updated_date
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_updated_time
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** What the own sheet total in the result card leads to: the tab, the total picker, disconnecting, connecting. */
class SubjectSheetActions(
    val onOpen: (url: String) -> Unit = {},
    val onChangeTotal: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onConnect: (List<SheetLinkOption>) -> Unit = {},
)

/** Test tags of the sheet total for host tests and the screen port. */
object SubjectSheetTotalTestTags {
    const val ROW = "subject_sheet_total"
    const val MENU = "subject_sheet_total_menu"
    const val HINT = "subject_sheet_total_hint"
}

/**
 * The bottom of the result card, for [SubjectHero]'s `sheet` slot (`item_subject_hero.xml`'s `sheet_divider`, `sheet`
 * and `sheet_hint`): a connected total under a hairline, or «Мои баллы из таблицы» that offers the subject's sheet
 * links.
 */
@Composable
fun SubjectSheetTotal(state: SubjectSheetState, actions: SubjectSheetActions) {
    when (state) {
        is SubjectSheetState.Connected -> {
            HorizontalDivider(
                Modifier.padding(top = ItmoTheme.spacing.group),
                color = ItmoTheme.colorScheme.outlineVariant,
            )
            ConnectedTotal(state, actions)
        }
        is SubjectSheetState.Hint -> ProgressButton(
            stringResource(Res.string.sheet_scores_hint),
            onClick = { actions.onConnect(state.links) },
            modifier = Modifier
                .padding(top = ItmoTheme.spacing.compact)
                .bleed(start = ButtonBleed, bottom = BottomBleed)
                .heightIn(min = ItmoTheme.spacing.touchTarget)
                .testTag(SubjectSheetTotalTestTags.HINT),
            style = ProgressButtonStyle.Text,
            icon = painterResource(KitRes.drawable.ic_table),
        )
    }
}

/**
 * The connected total (`item_subject_sheet_score.xml`): the value, «путь, лист «Лист»» and when it was read, or why
 * the last reading failed in the error colour (offline only says so and keeps its quiet colour). The row opens the
 * tab; `⋮` opens, changes the total or disconnects. The status runs under the menu, so it never wraps on a narrow
 * screen, and the menu's 48 dp target reaches into the card's padding so its glyph lines up with the content edge.
 */
@Composable
private fun ConnectedTotal(state: SubjectSheetState.Connected, actions: SubjectSheetActions) {
    val score = state.score
    val colors = ItmoTheme.colorScheme
    val value = score.value ?: stringResource(Res.string.recordbook_score_pending)
    val caption = sheetScoreCaption(score.tabName, sheetColumnTitle(score.column.headerPath, score.column.index))
    // Offline keeps the stored value and says only that; the time of a stale value would wrap on narrow screens.
    val status = if (score.status == SheetStatus.OK) state.updatedText() else score.status.labelResource()?.let {
        stringResource(it)
    }
    val quiet = score.status == SheetStatus.OK || score.status == SheetStatus.NETWORK
    val description = listOfNotNull(value, caption, status).filter { it.isNotEmpty() }.joinToString(LIST_SEPARATOR)
    Box(
        Modifier
            .padding(top = ItmoTheme.spacing.related)
            .bleed(end = MenuBleed, bottom = BottomBleed)
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .testTag(SubjectSheetTotalTestTags.ROW)
            .clickable { actions.onOpen(score.tabUrl) }
            .semantics { contentDescription = description },
    ) {
        Column(
            Modifier
                .padding(vertical = ItmoTheme.spacing.compact)
                .clearAndSetSemantics {},
        ) {
            Row(Modifier.padding(end = ItmoTheme.spacing.touchTarget), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(KitRes.drawable.ic_table),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    tint = colors.onSurfaceVariant,
                )
                Text(
                    value,
                    Modifier.padding(start = ItmoTheme.spacing.content),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ItmoTheme.typography.titleMedium,
                )
            }
            Text(
                caption,
                Modifier.padding(start = TextStart, top = LineGap, end = ItmoTheme.spacing.touchTarget),
                color = colors.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
            if (!status.isNullOrEmpty()) {
                Text(
                    status,
                    Modifier.padding(start = TextStart, top = LineGap, end = ItmoTheme.spacing.compact),
                    color = if (quiet) colors.onSurfaceVariant else colors.error,
                    style = ItmoTheme.typography.bodySmall,
                )
            }
        }
        SheetMenu(score, actions, Modifier.align(Alignment.TopEnd))
    }
}

/** `⋮`: open the tab, change the total, disconnect. */
@Composable
private fun SheetMenu(score: SheetScore, actions: SubjectSheetActions, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(Res.string.sheet_scores_actions)
    val items = listOf(
        ItmoMenuItem(stringResource(Res.string.sheet_scores_open), { actions.onOpen(score.tabUrl) }),
        ItmoMenuItem(stringResource(Res.string.sheet_scores_change_total), actions.onChangeTotal),
        ItmoMenuItem(stringResource(Res.string.sheet_scores_disconnect), actions.onDisconnect),
    )
    Box(
        modifier
            .size(ItmoTheme.spacing.touchTarget)
            .testTag(SubjectSheetTotalTestTags.MENU)
            .clickable(
                role = Role.Button,
                interactionSource = null,
                indication = ripple(bounded = false, radius = MenuRippleRadius),
                onClick = { expanded = true },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(KitRes.drawable.ic_more_vert),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
        ItmoMenu(expanded, onDismissRequest = { expanded = false }, groups = listOf(items))
    }
}

/** «Обновлено в 12:00» today, «Обновлено 7 сент.» before; nothing before the first reading. */
@Composable
private fun SubjectSheetState.Connected.updatedText(): String? = updatedAt?.let { at ->
    if (at.date == today) {
        stringResource(Res.string.sheet_scores_updated_time, at.time.format(DateTexts.TIME))
    } else {
        stringResource(Res.string.sheet_scores_updated_date, at.date.format(DateTexts.DAY_MONTH))
    }
}

/**
 * Lets the content reach [start], [end] and [bottom] into the parent's padding, as the XML's negative margins did:
 * it is measured that much wider and reports itself that much smaller.
 */
private fun Modifier.bleed(start: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp): Modifier = layout { measurable, c ->
    val horizontal = (start + end).roundToPx()
    val vertical = bottom.roundToPx()
    val wider = if (c.hasBoundedWidth) {
        c.copy(minWidth = c.minWidth + horizontal, maxWidth = c.maxWidth + horizontal)
    } else {
        c
    }
    val placeable = measurable.measure(wider)
    val width = (placeable.width - horizontal).coerceIn(c.minWidth, c.maxWidth)
    val maxHeight = if (c.hasBoundedHeight) c.maxHeight else Constraints.Infinity
    val height = (placeable.height - vertical).coerceIn(c.minHeight, maxHeight)
    layout(width, height) { placeable.place(-start.roundToPx(), 0) }
}

private const val LIST_SEPARATOR = ", "

/** The menu's and the text button's 12 dp of padding around their glyphs, and the row's 8 dp under its text. */
private val MenuBleed = 12.dp
private val ButtonBleed = 12.dp
private val BottomBleed = 8.dp

/** The 24 dp table and `⋮` glyphs. */
private val IconSize = 24.dp

/** The texts under the value start at its edge: the icon and its 12 dp gap. */
private val TextStart = 36.dp

/** The 2 dp `layout_marginTop` of the caption and the status. */
private val LineGap = 2.dp

/** `selectableItemBackgroundBorderless` over a 48 dp target. */
private val MenuRippleRadius = 24.dp

private val previewToday = LocalDate(2026, 3, 16)

private fun previewScore(
    value: String?,
    status: SheetStatus,
    tabName: String,
    header: String,
) = SheetScore(
        scope = ResourceScope(RecordbookPreviewSamples.MATH_ID, RecordbookPreviewSamples.MATH, "2025-2"),
        url = "https://docs.google.com/spreadsheets/d/preview/edit#gid=22",
        tabGid = 22,
        tabName = tabName,
        rowKey = "",
        keyColumn = 0,
        keyKind = KeyKind.ISU,
        column = SheetColumnRef(header, 11),
        value = value,
        baseline = value,
        tracked = true,
        status = status,
        updatedAt = null,
        connectedAt = Instant.parse("2026-03-01T09:00:00Z"),
    )

/** The result card of `RecordbookSubjectScreen_sheet`, an exam of [kind], with [sheet] at its bottom. */
@Composable
private fun SheetPreviewHero(kind: String, sheet: SubjectSheetState) {
    val subject = RecordbookPreviewSamples.subject(
        RecordbookPreviewSamples.MATH_ID, RecordbookPreviewSamples.MATH, kind, 72.0,
    )
    SubjectHero(
        subject,
        RecordbookGradeScale.nextStep(subject.score, subject.assessmentKind),
        Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin),
    ) { SubjectSheetTotal(sheet, SubjectSheetActions()) }
}

/** A total read at noon today. */
@Preview
@Composable
private fun SubjectSheetTotalPreview() = ItmoPreview {
    SheetPreviewHero(
        "Экзамен",
        SubjectSheetState.Connected(
            previewScore("66,3", SheetStatus.OK, "P3110", "ИТОГО баллов"),
            LocalDateTime(previewToday, LocalTime(12, 0)),
            previewToday,
        ),
    )
}

/** Read days ago, from a long tab and a header path with levels and no value yet. */
@Preview(name = "earlier")
@Composable
private fun SubjectSheetTotalEarlierPreview() = ItmoPreview {
    SheetPreviewHero(
        "Экзамен",
        SubjectSheetState.Connected(
            previewScore(
                null,
                SheetStatus.OK,
                tabName = "Баллы за весь семестр по всем видам работ",
                header = "Итог${SheetHeaders.SEPARATOR}Весь семестр${SheetHeaders.SEPARATOR}Баллы",
            ),
            LocalDateTime(2026, 3, 7, 9, 0),
            previewToday,
        ),
    )
}

/** The row is gone from the tab: the stored value stays, the failure in the error colour. */
@Preview(name = "failed")
@Composable
private fun SubjectSheetTotalFailedPreview() = ItmoPreview {
    SheetPreviewHero(
        "Экзамен",
        SubjectSheetState.Connected(
            previewScore("66,3", SheetStatus.ROW_NOT_FOUND, "P3110", "ИТОГО баллов"),
            updatedAt = null,
            today = previewToday,
        ),
    )
}

/** Offline only says so, in the quiet colour. */
@Preview(name = "offline")
@Composable
private fun SubjectSheetTotalOfflinePreview() = ItmoPreview {
    SheetPreviewHero(
        "Экзамен",
        SubjectSheetState.Connected(
            previewScore("66,3", SheetStatus.NETWORK, "P3110", "ИТОГО баллов"),
            LocalDateTime(previewToday, LocalTime(12, 0)),
            previewToday,
        ),
    )
}

/** No connection yet, but the subject has sheet links: the offer. */
@Preview(name = "hint")
@Composable
private fun SubjectSheetTotalHintPreview() = ItmoPreview {
    val link = SheetLinkOption("https://docs.google.com/spreadsheets/d/x", title = null, mine = true)
    SheetPreviewHero("Экзамен", SubjectSheetState.Hint(listOf(link)))
}
