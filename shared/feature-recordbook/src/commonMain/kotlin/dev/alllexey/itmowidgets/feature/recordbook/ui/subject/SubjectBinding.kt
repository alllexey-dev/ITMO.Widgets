package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeading
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeadingSpacing
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_binding_ambiguous
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_binding_confirm
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_binding_proposal
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_binding_reject
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_title
import org.jetbrains.compose.resources.stringResource

/**
 * The schedule found one subject that looks like this one (`item_subject_binding.xml` as `BindingHolder.bindProposal`
 * binds it): the question with the schedule's name, «Нет» and «Связать» at the end.
 */
@Composable
fun SubjectBindingProposal(candidate: ScheduleSubject, onConfirm: (Long) -> Unit, onReject: () -> Unit) {
    BindingCard(stringResource(Res.string.subject_binding_proposal, candidate.name)) {
        Row(
            Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.compact),
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact, Alignment.End),
        ) {
            ProgressButton(
                stringResource(Res.string.subject_binding_reject),
                onClick = onReject,
                style = ProgressButtonStyle.Text,
            )
            ProgressButton(
                stringResource(Res.string.subject_binding_confirm),
                onClick = { onConfirm(candidate.subjectId) },
                style = ProgressButtonStyle.Tonal,
            )
        }
    }
}

/** Several schedule subjects look like this one: each is a full-width button that binds it. */
@Composable
fun SubjectBindingChoice(candidates: List<ScheduleSubject>, onConfirm: (Long) -> Unit) {
    BindingCard(stringResource(Res.string.subject_binding_ambiguous)) {
        Column(Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.compact)) {
            candidates.forEach { candidate ->
                ProgressButton(
                    candidate.name,
                    onClick = { onConfirm(candidate.subjectId) },
                    modifier = Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.related),
                    style = ProgressButtonStyle.Tonal,
                )
            }
        }
    }
}

/** `Widget.ItmoWidgets.Card.Content.Outlined`: the content card with a hairline, the message above [actions]. */
@Composable
private fun BindingCard(message: String, actions: @Composable ColumnScope.() -> Unit) {
    val shape = ItmoTheme.shapes.cardContent
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = ItmoTheme.spacing.compact)
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLow)
            .border(ItmoTheme.shapes.cardStroke, ItmoTheme.colorScheme.outlineVariant, shape)
            .padding(ItmoTheme.spacing.cardPadding),
    ) {
        Text(message, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyMedium)
        actions()
    }
}

@Composable
private fun BindingPreviewColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(bottom = ItmoTheme.spacing.screenMargin),
    ) {
        SectionHeading(stringResource(Res.string.subject_lessons_title), spacing = SectionHeadingSpacing.First)
        content()
    }
}

/** `RecordbookSubjectScreen_binding`'s proposal. */
@Preview
@Composable
private fun SubjectBindingProposalPreview() = ItmoPreview {
    BindingPreviewColumn {
        SubjectBindingProposal(ScheduleSubject(2, "Алгоритмы и структуры данных", setOf(10)), {}, {})
    }
}

/** Two flows of the same subject under different names, one of them long. */
@Preview
@Composable
private fun SubjectBindingChoicePreview() = ItmoPreview {
    BindingPreviewColumn {
        SubjectBindingChoice(
            listOf(
                ScheduleSubject(21, "Алгоритмы и структуры данных", setOf(10)),
                ScheduleSubject(22, "Алгоритмы и структуры данных (продвинутый уровень, поток ПИиКТ)", setOf(11)),
            ),
            onConfirm = {},
        )
    }
}
