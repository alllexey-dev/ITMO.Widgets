package dev.alllexey.itmowidgets.feature.schedule.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonFriendsState

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LS-0 recorded the XML references as
 * `LessonDetailsContent_<state>`. Each state therefore is a function called `LessonDetailsContent` in a holder class
 * of its own. Every preview is the sheet at its 90 % of the 891 dp capture window, on the sheet's colour, as
 * `LessonDetailsBottomSheet` opens it.
 */

/** Teacher with a profile, flow, room, the change, link, note and two friends. */
internal class LessonDetailsFullPreview {
    @Preview(name = "full", heightDp = SHEET_HEIGHT)
    @Composable
    fun LessonDetailsContent() = DetailsPreview(
        LessonDetailsSamples.full,
        LessonDetailsUiState(
            friends = LessonFriendsState.Content(LessonDetailsSamples.friends),
            change = LessonDetailsSamples.roomChange,
        ),
    )
}

/** No teacher, room or link, an unknown subject and no opt-in: only the time stays. */
internal class LessonDetailsMinimalPreview {
    @Preview(name = "minimal", heightDp = SHEET_HEIGHT)
    @Composable
    fun LessonDetailsContent() = DetailsPreview(
        LessonDetailsSamples.minimal,
        LessonDetailsUiState(friends = LessonFriendsState.Disabled),
        mapAvailable = false,
    )
}

internal class LessonDetailsFriendsLoadingPreview {
    @Preview(name = "friends-loading", heightDp = SHEET_HEIGHT)
    @Composable
    fun LessonDetailsContent() = DetailsPreview(LessonDetailsSamples.plain, LessonDetailsUiState())
}

internal class LessonDetailsFriendsErrorPreview {
    @Preview(name = "friends-error", heightDp = SHEET_HEIGHT)
    @Composable
    fun LessonDetailsContent() = DetailsPreview(
        LessonDetailsSamples.plain,
        LessonDetailsUiState(friends = LessonFriendsState.Error(AppError.Network)),
    )
}

internal class LessonDetailsFriendsEmptyPreview {
    @Preview(name = "friends-empty", heightDp = SHEET_HEIGHT)
    @Composable
    fun LessonDetailsContent() = DetailsPreview(
        LessonDetailsSamples.plain,
        LessonDetailsUiState(friends = LessonFriendsState.Content(emptyList())),
    )
}

/** The tone dot between the teacher's name and the chevron. */
internal class LessonDetailsTeacherLevelPreview {
    @Preview(name = "teacher-level", heightDp = SHEET_HEIGHT)
    @Composable
    fun LessonDetailsContent() = DetailsPreview(
        LessonDetailsSamples.plain.copy(teacherIsu = LessonDetailsSamples.TEACHER_ISU.toLong()),
        LessonDetailsUiState(friends = LessonFriendsState.Content(emptyList()), teacherLevel = TeacherLevel.POSITIVE),
    )
}

/** A flow name too long for one line wraps under its icon. */
internal class LessonDetailsLongFlowPreview {
    @Preview(name = "long-flow", heightDp = SHEET_HEIGHT)
    @Composable
    fun LessonDetailsContent() = DetailsPreview(
        LessonDetailsSamples.plain.copy(flowName = LessonDetailsSamples.LONG_FLOW),
        LessonDetailsUiState(friends = LessonFriendsState.Disabled),
    )
}

/** Time and room changed at once: one "было -> стало" line per field. */
internal class LessonDetailsChangePreview {
    @Preview(name = "change", heightDp = SHEET_HEIGHT)
    @Composable
    fun LessonDetailsContent() = DetailsPreview(
        LessonDetailsSamples.plain,
        LessonDetailsUiState(friends = LessonFriendsState.Disabled, change = LessonDetailsSamples.timeAndRoomChange),
    )
}

@Composable
private fun DetailsPreview(
    lesson: LessonDetailsArgs,
    details: LessonDetailsUiState,
    mapAvailable: Boolean = true,
) = ItmoPreview {
    val shape = ItmoTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLowest),
    ) {
        LessonDetailsContent(
            LessonDetailsSheetState(lesson, details, mapAvailable),
            LessonDetailsActions(),
            Modifier.fillMaxSize(),
        )
    }
}

/** 90 % of the 891 dp capture window, the sheet's `SheetHeight.Tall`. */
private const val SHEET_HEIGHT = 802
