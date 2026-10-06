package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview

/** The day's lessons, one with a request in flight. */
@Preview(name = "content")
@Composable
private fun SportLessonListPreview() = ItmoPreview {
    ListFrame(
        SportLessonListState.Lessons(
            listOf(SportLessonSamples.scarce, SportLessonSamples.open, SportLessonSamples.full),
            busyLessonIds = setOf(SportLessonSamples.open.lessonId),
        ),
    )
}

/** The catalogue has not answered yet. */
@Preview(name = "loading")
@Composable
private fun SportLessonListLoadingPreview() = ItmoPreview { ListFrame(SportLessonListState.Loading) }

/** Nothing on the day under the filters. */
@Preview(name = "empty")
@Composable
private fun SportLessonListEmptyPreview() = ItmoPreview { ListFrame(SportLessonListState.Empty(filtered = true)) }

/** The catalogue failed with nothing to keep. */
@Preview(name = "error")
@Composable
private fun SportLessonListErrorPreview() = ItmoPreview { ListFrame(SportLessonListState.Error(AppError.Network)) }

@Composable
private fun ListFrame(state: SportLessonListState) {
    SportLessonList(state, SportLessonSamples.time, SportLessonActions(), onRetry = {})
}
