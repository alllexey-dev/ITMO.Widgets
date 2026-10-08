package dev.alllexey.itmowidgets.app.shell.entries

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewEvent
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReportReviewViewModel
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorEvent
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import dev.alllexey.itmowidgets.feature.reviews.ui.ReportReviewForm
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorActions
import dev.alllexey.itmowidgets.feature.reviews.ui.ReviewEditorSheet
import org.koin.core.parameter.parametersOf

/**
 * The reviews keys (route map S8, D1): the review editor, a form sheet that asks before a draft is discarded, and the
 * report of a review. Both read the teacher under `TeacherReviewArgs`, as the Fragment hosts do.
 */
internal fun EntryRegistry.Builder.reviewsEntries() {
    entry<AppRoutes.ReviewEditor>(args = { it.args.toArguments() }) { key, navigator ->
        ReviewEditorEntry(onClose = { navigator.close(key) })
    }
    entry<AppRoutes.ReportReview>(
        args = { it.args.toArguments().apply { putString(TeacherReviewArgs.REVIEW_ID, it.reviewId) } },
    ) { key, navigator ->
        ReportReviewEntry(onClose = { navigator.close(key) })
    }
}

private fun TeacherReviewArgs.toArguments() =
    bundleOf(TeacherReviewArgs.TEACHER_ISU to teacherIsu, TeacherReviewArgs.TEACHER_NAME to teacherName)

/**
 * The review editor (S8). The shell's form sheet ignores drag, a tap outside and its own Back, so Back and the close
 * button come here: unsaved changes ask `Не сохранять отзыв?` first (not restored after recreation, like the View
 * dialog), an untouched form closes. A saved review closes the sheet; a failed save says so.
 */
@Composable
private fun ReviewEditorEntry(onClose: () -> Unit) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: ReviewEditorViewModel = entryViewModel()
    val form by viewModel.uiState.collectAsStateWithLifecycle()
    var discarding by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    val requestClose = { if (viewModel.hasChanges()) discarding = true else close() }
    BackHandler(onBack = requestClose)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    ReviewEditorEvent.Saved -> close()
                    is ReviewEditorEvent.Failed ->
                        Snackbar.make(view, event.error.messageRes(), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }
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

/**
 * The report of a review (D1) on the kit's report dialog, which stays until the report is accepted; a failure shows
 * under the comment until the next send.
 */
@Composable
private fun ReportReviewEntry(onClose: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: ReportReviewViewModel = entryViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    ReportReviewEvent.Done -> onClose()
                    is ReportReviewEvent.Failed -> failure = event.error
                }
            }
        }
    }
    DialogSceneAnchor()
    ReportReviewForm(
        sending = state.sending,
        error = failure,
        onSend = { reason, comment ->
            failure = null
            viewModel.send(reason, comment)
        },
        onDismiss = onClose,
    )
}

/**
 * Koin's definition of [VM] in this entry's own store, with the entry's `SavedStateHandle` (its arguments, seeded by
 * the shell) as Koin's `koinViewModel()` passes it.
 */
@Composable
private inline fun <reified VM : ViewModel> entryViewModel(): VM {
    val context = LocalContext.current
    return viewModel { KoinStarter.ensureStarted(context).get<VM> { parametersOf(createSavedStateHandle()) } }
}
