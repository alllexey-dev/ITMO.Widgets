package dev.alllexey.itmowidgets.feature.schedule.ui.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClosePlacement
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsViewModel
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.schedule_lesson_details_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_close

/**
 * The lesson details sheet of [lesson] with its view model: the host-facing entry the Fragment host and the SwiftUI
 * host call. The view model reads the occurrence from its saved state (`ARG_PAIR_ID`, `ARG_DATE`, `ARG_TEACHER_ISU`);
 * `Повторить` asks it for the friends again, every other action goes to the host.
 */
@Composable
fun LessonDetailsSheetRoute(
    lesson: LessonDetailsArgs,
    mapAvailable: Boolean,
    actions: LessonDetailsActions,
    modifier: Modifier = Modifier,
    viewModel: LessonDetailsViewModel = koinViewModel(),
) {
    val details by viewModel.uiState.collectAsStateWithLifecycle()
    val withRetry = remember(actions, viewModel) {
        LessonDetailsActions(
            onMap = actions.onMap,
            onLink = actions.onLink,
            onProfile = actions.onProfile,
            onRetryFriends = { viewModel.refresh(RefreshMode.Force) },
            onClose = actions.onClose,
        )
    }
    LessonDetailsContent(LessonDetailsSheetState(lesson, details, mapAvailable), withRetry, modifier)
}

/**
 * The body of the lesson details sheet (`fragment_lesson_details.xml`): the toolbar with close, the kit's
 * `DetailsHeader` (subject, type and format, date and time, teacher with the reserved tone dot, flow, place and the
 * map), then, each only when present, `Изменения`, the meeting info with `Открыть видеозвонок`, the note and
 * `Друзья на паре`. Stateless; the host owns the sheet's container (`colorSurfaceContainerLowest`, 90 % of the
 * screen) and every effect.
 */
@Composable
fun LessonDetailsContent(
    state: LessonDetailsSheetState,
    actions: LessonDetailsActions,
    modifier: Modifier = Modifier,
) {
    val lesson = state.lesson
    SheetScaffold(
        title = stringResource(Res.string.schedule_lesson_details_title),
        modifier = modifier,
        close = SheetClose(stringResource(CoreRes.string.common_close), actions.onClose),
        closePlacement = SheetClosePlacement.Start,
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(LessonDetailsTestTags.SCROLL)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ItmoTheme.spacing.screenMargin,
                    end = ItmoTheme.spacing.screenMargin,
                    bottom = ItmoTheme.spacing.section,
                ),
        ) {
            LessonDetailsHeader(state, actions)
            state.details.change?.let { LessonChangeSection(it) }
            LessonLinkBlock(lesson, actions.onLink)
            lesson.note?.takeIf { it.isNotBlank() }?.let { LessonNoteSection(it) }
            LessonFriendsSection(state.details.friends, actions)
        }
    }
}
