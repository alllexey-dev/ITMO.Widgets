package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lessons_empty_description
import dev.alllexey.itmowidgets.shared.feature.sport.sport_lessons_empty_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_event_note

/** The area under the `Запись` filters and week strip. */
@Immutable
sealed interface SportLessonListState {

    /** No catalogue yet: placeholder cards. */
    data object Loading : SportLessonListState

    /** The day's lessons; those in [busyLessonIds] have a request in flight. */
    data class Lessons(val lessons: List<SportLesson>, val busyLessonIds: Set<Long> = emptySet()) : SportLessonListState

    /**
     * Nothing on the selected day, with or without filters ([filtered]): the same text either way, since it already
     * suggests another day or fewer filters, and `Доступные` filters by default without counting as a filter.
     */
    data class Empty(val filtered: Boolean) : SportLessonListState

    /** The catalogue failed to load and there is nothing to keep: the error and a retry. */
    data class Error(val error: AppError) : SportLessonListState
}

/** The list area of a `Запись` state: the header and calendar are real while the catalogue loads. */
fun SportSignUiState.lessonListState(): SportLessonListState = when (this) {
    SportSignUiState.Loading -> SportLessonListState.Loading
    is SportSignUiState.Error -> SportLessonListState.Error(error)
    is SportSignUiState.Content -> when {
        initialLoading -> SportLessonListState.Loading
        displayedLessons.isEmpty() -> SportLessonListState.Empty(filtered = hasActiveFilters)
        else -> SportLessonListState.Lessons(displayedLessons, busyLessonIds)
    }
}

/**
 * The `Запись` lessons as a list of their own, with [header] items above them (the filters and the week strip,
 * LP-5c). A screen whose list has more above it adds [sportLessons] to its own `LazyColumn` instead.
 */
@Composable
fun SportLessonList(
    state: SportLessonListState,
    time: AcademicTimeProvider,
    actions: SportLessonActions,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    header: LazyListScope.() -> Unit = {},
) {
    LazyColumn(modifier.testTag(SportLessonListTestTags.LIST), listState, contentPadding) {
        header()
        sportLessons(state, time, actions, onRetry)
    }
}

/**
 * The lesson cards of [state], or its placeholder, empty or error state inline in the same area. Cards are keyed by
 * lesson id and reality, so a prediction and the real lesson that replaces it never share a slot.
 */
fun LazyListScope.sportLessons(
    state: SportLessonListState,
    time: AcademicTimeProvider,
    actions: SportLessonActions,
    onRetry: () -> Unit,
) {
    when (state) {
        SportLessonListState.Loading -> item(key = SKELETON_KEY, contentType = SKELETON_KEY) {
            Skeleton(SkeletonStyle.Cards, Modifier.testTag(SportLessonListTestTags.SKELETON))
        }
        is SportLessonListState.Empty -> item(key = STATE_KEY, contentType = STATE_KEY) {
            ContentState(
                title = stringResource(Res.string.sport_lessons_empty_title),
                modifier = Modifier.testTag(SportLessonListTestTags.EMPTY),
                size = ContentStateSize.Compact,
                icon = painterResource(KitRes.drawable.ic_event_note),
                description = stringResource(Res.string.sport_lessons_empty_description),
            )
        }
        is SportLessonListState.Error -> item(key = STATE_KEY, contentType = STATE_KEY) {
            ContentState(
                title = stringResource(CoreRes.string.common_load_error_title),
                modifier = Modifier.testTag(SportLessonListTestTags.ERROR),
                size = ContentStateSize.Compact,
                icon = painterResource(KitRes.drawable.ic_error),
                description = stringResource(state.error.textResource()),
                action = ContentStateAction(stringResource(CoreRes.string.common_retry), onRetry),
            )
        }
        is SportLessonListState.Lessons -> items(
            state.lessons,
            key = { lesson -> sportLessonKey(lesson) },
            contentType = { LESSON_CONTENT_TYPE },
        ) { lesson ->
            SportLessonCard(lesson, time, actions, busy = lesson.lessonId in state.busyLessonIds)
        }
    }
}

/** The list key of a lesson card: its id and whether it is real ("17:true", "17:false"). */
fun sportLessonKey(lesson: SportLesson): String = "${lesson.lessonId}:${lesson.isLessonReal}"

/** Tags for host tests and the screen's instrumented flow. */
object SportLessonListTestTags {
    const val LIST = "sport_lesson_list"
    const val SKELETON = "sport_lesson_skeleton"
    const val EMPTY = "sport_lesson_empty"
    const val ERROR = "sport_lesson_error"
}

private const val SKELETON_KEY = "sport_lessons_skeleton"
private const val STATE_KEY = "sport_lessons_state"
private const val LESSON_CONTENT_TYPE = "sport_lesson"
