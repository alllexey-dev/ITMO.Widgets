package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.ActionCard
import dev.alllexey.itmowidgets.designsystem.kit.GateCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.PersonListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectBinding
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonList
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessons
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectReviews
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_note
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_binding_ambiguous
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_binding_confirm
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_binding_proposal
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_binding_reject
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_connect
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_all
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_none
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_lessons_unmatched
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_reviews_few_text
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_reviews_few_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_reviews_off_text
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_reviews_off_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_reviews_write
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_teachers_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.designsystem.components.buttons.TonalButton

/** `Пары`: the teachers as rows with the tone of their reviews in words, then the lessons ahead. */
internal fun LazyListScope.lessonsTab(lessons: SubjectLessons, actions: RecordbookSubjectActions) {
    lessons.binding?.let { binding -> item(key = "binding") { Binding(binding, actions) } }
    if (lessons.teachers.isNotEmpty()) {
        item(key = "teachers-title") { SectionHeader(stringResource(Res.string.subject_teachers_title)) }
        item(key = "teachers") {
            ListGroup {
                lessons.teachers.forEachIndexed { index, teacher ->
                    item {
                        PersonListRow(
                            teacher.name,
                            pictureUrl = null,
                            Modifier.testTag(SubjectTestTags.teacher(index)),
                            subtitle = teacher.detail?.asString(),
                            chevron = teacher.isu != null,
                            onClick = teacher.isu?.let { isu -> { actions.onOpenTeacher(isu) } },
                        )
                    }
                }
            }
        }
    }
    when (val reviews = lessons.reviews) {
        SubjectReviews.Shown -> Unit
        is SubjectReviews.Few -> item(key = "reviews") {
            ActionCard(
                title = stringResource(Res.string.subject_reviews_few_title),
                reason = stringResource(Res.string.subject_reviews_few_text),
                action = stringResource(Res.string.subject_reviews_write),
                onAction = { actions.onWriteReview(reviews.teacher) },
                icon = painterResource(KitRes.drawable.ic_edit_note),
            )
        }
        SubjectReviews.Off -> item(key = "reviews") {
            GateCard(
                title = stringResource(Res.string.subject_reviews_off_title),
                reason = stringResource(Res.string.subject_reviews_off_text),
                fix = stringResource(Res.string.subject_connect),
                onFix = actions.onConnect,
                modifier = Modifier.testTag(SubjectTestTags.REVIEWS_GATE),
                icon = painterResource(KitRes.drawable.ic_cloud_off),
            )
        }
    }
    lessonList(lessons.list, actions)
}

private fun LazyListScope.lessonList(list: SubjectLessonList, actions: RecordbookSubjectActions) {
    item(key = "lessons-title") { SectionHeader(stringResource(Res.string.subject_lessons_title)) }
    when (list) {
        SubjectLessonList.None -> item(key = "lessons-none") { ListGroupNote(stringResource(Res.string.subject_lessons_none)) }
        SubjectLessonList.Loading -> item(key = "lessons-loading") { ContentStateLoading(size = ContentStateSize.Compact) }
        is SubjectLessonList.Failed -> item(key = "lessons-error") {
            ListGroup {
                item {
                    ListRow(
                        title = stringResource(CoreRes.string.common_load_error_title),
                        subtitle = list.error.toUiText().asString(),
                        accessory = { TextButton(onClick = actions.onRetryLessons) { Text(stringResource(CoreRes.string.common_retry)) } },
                    )
                }
            }
        }
        SubjectLessonList.Unmatched -> item(key = "lessons-unmatched") {
            ListGroupNote(stringResource(Res.string.subject_lessons_unmatched))
        }
        is SubjectLessonList.Content -> {
            list.items.forEach { item ->
                item(key = "lesson-${item.pairId}") { SubjectLessonCard(item, actions.onOpenLesson) }
            }
            if (list.more > 0) {
                item(key = "lessons-all") {
                    ListGroup {
                        item {
                            ListRow(
                                title = stringResource(Res.string.subject_lessons_all, list.more),
                                chevron = true,
                                onClick = actions.onShowAllLessons,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Whether the schedule's subject is this one: yes or no for one candidate, a choice of several. */
@Composable
private fun Binding(binding: SubjectBinding, actions: RecordbookSubjectActions) {
    val single = binding.candidates.singleOrNull()
    ItmoCard {
        Text(
            if (single != null) stringResource(Res.string.subject_binding_proposal, single.name)
            else stringResource(Res.string.subject_binding_ambiguous),
            style = ItmoTheme.typography.bodyLarge,
        )
        if (single != null) {
            Row(Modifier.padding(top = ItmoTheme.spacing.compact), horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
                TonalButton(onClick = { actions.onConfirmBinding(single.subjectId) }) {
                    Text(stringResource(Res.string.subject_binding_confirm))
                }
                OutlinedButton(onClick = actions.onRejectBinding) { Text(stringResource(Res.string.subject_binding_reject)) }
            }
        } else {
            binding.candidates.forEach { candidate ->
                TextButton(onClick = { actions.onConfirmBinding(candidate.subjectId) }) { Text(candidate.name) }
            }
            TextButton(onClick = actions.onRejectBinding) { Text(stringResource(Res.string.subject_binding_reject)) }
        }
    }
}
