package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.charts.GradeScale
import dev.alllexey.itmowidgets.designsystem.components.charts.GradeScaleTick
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ChoiceDialog
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenu
import dev.alllexey.itmowidgets.designsystem.components.menus.ItmoMenuItem
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.PillSpec
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.ink
import dev.alllexey.itmowidgets.designsystem.kit.tabular
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookTone
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectControlGroup
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectControlRow
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectHero
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectScores
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetLine
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetOption
import dev.alllexey.itmowidgets.feature.recordbook.ui.BarsNotice
import dev.alllexey.itmowidgets.feature.recordbook.ui.BarsOfferRow
import dev.alllexey.itmowidgets.feature.recordbook.ui.kit
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_more_vert
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import dev.alllexey.itmowidgets.shared.designsystem.ic_table
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_details_empty_description
import dev.alllexey.itmowidgets.shared.feature.recordbook.recordbook_score_pending
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_actions
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_change_total
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_choose_link
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_disconnect
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_hint
import dev.alllexey.itmowidgets.shared.feature.recordbook.sheet_scores_open
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_control_absent
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_score_out_of
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_sheet_offer_note
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_sport_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** `Баллы`: the hero, the sport points of physical education, the checkpoints by group, or the offers without them. */
internal fun LazyListScope.scoresTab(scores: SubjectScores, actions: RecordbookSubjectActions) {
    scores.barsNotice?.let { notice -> item(key = "bars-notice") { BarsNotice(notice, actions.onBarsSignIn, actions.onRefresh) } }
    item(key = "hero") { Hero(scores.hero, scores.sheet, actions) }
    scores.sport?.let { sport ->
        item(key = "sport") {
            ListGroup {
                item {
                    ListRow(
                        title = stringResource(Res.string.subject_sport_title),
                        icon = painterResource(KitRes.drawable.ic_sprint),
                        iconTone = Tone.Success,
                        trailing = sport.asString(),
                        chevron = true,
                        onClick = actions.onOpenSport,
                    )
                }
            }
        }
    }
    scores.groups.forEachIndexed { index, group ->
        group.title?.let { title -> item(key = "group-title-$index") { GroupTitle(title.asString(), group.sum?.asString(), group.below) } }
        item(key = "group-$index") { ControlGroup(group) }
    }
    scores.controlsError?.let { error ->
        item(key = "controls-error") {
            ListGroup {
                item {
                    ListRow(
                        title = stringResource(CoreRes.string.common_load_error_title),
                        subtitle = error.toUiText().asString(),
                        accessory = { TextButton(onClick = actions.onRefresh) { Text(stringResource(CoreRes.string.common_retry)) } },
                    )
                }
            }
        }
    }
    if (scores.barsOffer || scores.sheetOffer.isNotEmpty()) {
        item(key = "offers") {
            ListGroup {
                if (scores.barsOffer) item { BarsOfferRow(actions.onBarsSignIn) }
                if (scores.sheetOffer.isNotEmpty()) item { SheetOfferRow(scores.sheetOffer, actions) }
            }
        }
    }
    if (scores.noDetails) {
        item(key = "no-details") { ListGroupNote(stringResource(Res.string.recordbook_details_empty_description)) }
    }
}

/** The points over `из 100`, the grade pill and its source, the scale, the next step; the connected sheet under them. */
@Composable
private fun Hero(hero: SubjectHero, sheet: SubjectSheetLine?, actions: RecordbookSubjectActions) {
    ItmoCard(Modifier.testTag(SubjectTestTags.HERO), tone = Tone.Primary, hero = true) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
            Text(
                hero.points ?: stringResource(Res.string.recordbook_score_pending),
                style = ItmoTheme.typography.displayMedium.tabular(),
            )
            Text(
                stringResource(Res.string.subject_score_out_of),
                Modifier.padding(bottom = ItmoTheme.spacing.compact).weight(1f),
                style = ItmoTheme.typography.titleMedium,
            )
            Column(
                Modifier.padding(bottom = ItmoTheme.spacing.compact),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
            ) {
                hero.grade?.let { StatusPill(PillSpec(it.asString(), hero.gradeTone.kit(), solid = hero.gradeTone == RecordbookTone.PRIMARY)) }
                Text(hero.source.asString(), style = ItmoTheme.typography.labelSmall)
            }
        }
        GradeScale(
            score = (hero.share * MAX_POINTS).toDouble().takeIf { hero.points != null },
            ticks = hero.ticks.map { GradeScaleTick(it.score, it.label.asString()) },
            fillColor = ItmoTheme.colorScheme.primary,
        )
        hero.next?.let { Text(it.asString(), style = ItmoTheme.typography.titleSmall) }
        if (sheet != null) SheetLine(sheet, actions)
    }
}

/** The connected sheet inside the hero, with its menu: open, change the total, disconnect. */
@Composable
private fun SheetLine(sheet: SubjectSheetLine, actions: RecordbookSubjectActions) {
    var menu by remember { mutableStateOf(false) }
    HorizontalDivider(Modifier.padding(vertical = ItmoTheme.spacing.compact), color = ItmoTheme.colorScheme.outlineVariant)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
        Icon(painterResource(KitRes.drawable.ic_table), contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(sheet.title.asString(), style = ItmoTheme.typography.titleSmall)
            Text(sheet.caption.asString(), style = ItmoTheme.typography.bodySmall)
            sheet.problem?.let { Text(it.asString(), color = Tone.Warning.ink(), style = ItmoTheme.typography.bodySmall) }
            sheet.check?.let { check ->
                TextButton(
                    onClick = { actions.onOpenSheetScores(sheet.changeTotal) },
                    modifier = Modifier.heightIn(min = ItmoTheme.spacing.touchTarget),
                    contentPadding = PaddingValues(0.dp),
                    // The hero's own ink: the primary colour would vanish on the primary card.
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                ) {
                    Text(check.asString(), style = ItmoTheme.typography.labelLarge, textDecoration = TextDecoration.Underline)
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(KitRes.drawable.ic_more_vert), contentDescription = stringResource(Res.string.sheet_scores_actions))
            }
            ItmoMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                groups = listOf(
                    listOf(
                        ItmoMenuItem(stringResource(Res.string.sheet_scores_open), { actions.onOpenLink(sheet.url) }),
                        ItmoMenuItem(stringResource(Res.string.sheet_scores_change_total), { actions.onOpenSheetScores(sheet.changeTotal) }),
                    ),
                    listOf(ItmoMenuItem(stringResource(Res.string.sheet_scores_disconnect), actions.onDisconnectSheet, destructive = true)),
                ),
            )
        }
    }
}

/** `Мои баллы из таблицы`: one link opens its sheet at once, several ask which. */
@Composable
private fun SheetOfferRow(options: List<SubjectSheetOption>, actions: RecordbookSubjectActions) {
    var choosing by remember { mutableStateOf(false) }
    ListRow(
        title = stringResource(Res.string.sheet_scores_hint),
        subtitle = stringResource(Res.string.subject_sheet_offer_note),
        icon = painterResource(KitRes.drawable.ic_table),
        iconTone = Tone.Neutral,
        chevron = true,
        onClick = { if (options.size == 1) actions.onOpenSheetScores(options.single().args) else choosing = true },
    )
    if (choosing) {
        ChoiceDialog(
            title = stringResource(Res.string.sheet_scores_choose_link),
            options = options.map { it.title.asString() },
            selectedIndex = null,
            onSelect = { index ->
                choosing = false
                actions.onOpenSheetScores(options[index].args)
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun GroupTitle(title: String, sum: String?, below: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = ItmoTheme.spacing.related, end = ItmoTheme.spacing.related, top = ItmoTheme.spacing.compact),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        Text(title, Modifier.weight(1f), color = ItmoTheme.colorScheme.primary, style = ItmoTheme.typography.titleSmall)
        if (sum != null) {
            Text(
                sum,
                color = if (below) Tone.Error.ink() else ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.titleSmall.tabular(),
            )
        }
    }
}

@Composable
private fun ControlGroup(group: SubjectControlGroup) {
    ListGroup { group.rows.forEach { row -> item { ControlRow(row) } } }
}

/** A checkpoint: name, `неявка`, `9 / 10`; the bar once graded; `минимум 6` on the left and the date on the right. */
@Composable
private fun ControlRow(row: SubjectControlRow) {
    Column(
        Modifier
            .fillMaxWidth()
            // One stop for TalkBack: the bar is read with the name and the score.
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
            Text(row.name, Modifier.weight(1f), style = ItmoTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (row.absent) StatusPill(stringResource(Res.string.subject_control_absent), tone = Tone.Error)
            Text(
                row.score.asString(),
                style = ItmoTheme.typography.titleSmall.tabular(),
                color = if (row.graded) ItmoTheme.colorScheme.onSurface else ItmoTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.share?.let { share ->
            LinearProgressIndicator(
                progress = { share },
                modifier = Modifier.fillMaxWidth().height(BarHeight),
                color = row.tone.kit().ink(),
                trackColor = ItmoTheme.progressTrack,
                drawStopIndicator = {},
            )
        }
        if (row.minimum != null || row.date != null) {
            Row {
                Text(
                    row.minimum?.asString().orEmpty(),
                    Modifier.weight(1f),
                    color = if (row.below) Tone.Error.ink() else ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodySmall,
                )
                row.date?.let { Text(it.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodySmall) }
            }
        }
    }
}

private const val MAX_POINTS = 100f

/** A checkpoint's bar: thin, as the list's meters. */
private val BarHeight = 4.dp
