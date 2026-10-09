package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewEvent
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorEvent
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import dev.alllexey.itmowidgets.feature.reviews.ui.ReportReviewForm
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorActions
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorSheet
import platform.UIKit.UIViewController

/**
 * The review editor (`AppRoutes.ReviewEditor`, from a teacher's profile) inside a SwiftUI sheet that ignores a drag
 * down, as Android's `ReviewEditorEntry` in its form sheet: the close button asks `Не сохранять отзыв?` over the form
 * when it has unsaved changes (the dialog stays Compose) and closes an untouched one through [onClose]; a saved review
 * closes the sheet, a failed save goes to [say] as its text and the form stays.
 */
fun reviewEditorViewController(
    args: TeacherReviewArgs,
    say: (String) -> Unit,
    onClose: () -> Unit,
): UIViewController = screenController(opaque = false) {
    val viewModel = hostedViewModel<ReviewEditorViewModel>(*args.handleEntries())
    val form by viewModel.uiState.collectAsState()
    var discarding by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ReviewEditorEvent.Saved -> close()
                is ReviewEditorEvent.Failed -> say(event.error.text())
            }
        }
    }
    val requestClose = { if (viewModel.hasChanges()) discarding = true else close() }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
    ) {
        ReviewEditorSheet(
            form = form,
            teacherName = viewModel.teacherName,
            discarding = discarding,
            actions = ReviewEditorActions(
                onSubjectChange = viewModel::onSubjectChanged,
                onTextChange = viewModel::onTextChanged,
                onAnonymousChange = viewModel::onAnonymousChanged,
                onSave = viewModel::save,
                onClose = requestClose,
                onDiscard = close,
                onKeepEditing = { discarding = false },
            ),
        )
    }
}

/**
 * The report of a review (`AppRoutes.ReportReview`), as Android's `ReportReviewEntry`, over the profile it was asked
 * from: the kit's report dialog stays until the report is accepted ([onDone]); a failure shows under the comment until
 * the next send.
 */
@Composable
internal fun ReportReviewHosted(args: TeacherReviewArgs, reviewId: String, onDone: () -> Unit, onDismiss: () -> Unit) {
    val viewModel = hostedViewModel<ReportReviewViewModel>(
        *args.handleEntries(),
        TeacherReviewArgs.REVIEW_ID to reviewId,
    )
    val state by viewModel.uiState.collectAsState()
    var failure by remember { mutableStateOf<AppError?>(null) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ReportReviewEvent.Done -> done()
                is ReportReviewEvent.Failed -> failure = event.error
            }
        }
    }
    ReportReviewForm(
        sending = state.sending,
        error = failure,
        onSend = { reason, comment ->
            failure = null
            viewModel.send(reason, comment)
        },
        onDismiss = onDismiss,
    )
}

/** Android's `TeacherReviewArgs.toArguments()`. */
private fun TeacherReviewArgs.handleEntries(): Array<Pair<String, Any?>> = arrayOf(
    TeacherReviewArgs.TEACHER_ISU to teacherIsu,
    TeacherReviewArgs.TEACHER_NAME to teacherName,
)

private suspend fun AppError.text(): String = toUiText().resolve()
