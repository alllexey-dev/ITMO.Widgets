package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsActions
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsSheetRoute
import dev.alllexey.itmowidgets.feature.resources.ui.LinkEditorSheetRoute
import dev.alllexey.itmowidgets.feature.resources.ui.ReportLinkForm
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksSheetRoute
import dev.alllexey.itmowidgets.ios.di.IosKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * `Все ссылки` (`AppRoutes.SubjectLinks`, from the subject page) inside a SwiftUI sheet, as Android's
 * `SubjectLinksEntry`: a link opens in the system ([linkFailed] when nothing takes it), a link's actions and the editor
 * go to [open] (Swift shows them over this sheet), a failed vote or refresh goes to [say] as its text.
 */
fun subjectLinksViewController(
    args: SubjectLinksArgs,
    open: (AppRoute) -> Unit,
    linkFailed: () -> Unit,
    say: (String) -> Unit,
    onClose: () -> Unit,
): UIViewController {
    val actions = IosKoin.koin().get<PlatformActions>()
    return linkSheetController {
        val scope = rememberCoroutineScope()
        SubjectLinksSheetRoute(
            viewModel = hostedViewModel<SubjectLinksViewModel>(*args.handleEntries()),
            onOpen = { link -> if (!actions.openLink(link.url)) linkFailed() },
            onLinkActions = { link -> open(AppRoutes.LinkActions(args, link.id)) },
            onAdd = { open(AppRoutes.LinkEditor(args)) },
            onClose = onClose,
            onFailure = { error -> scope.say(error, say) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The link editor (`AppRoutes.LinkEditor`: a new link, or the viewer's own [AppRoutes.LinkEditor.linkId]) inside a
 * SwiftUI sheet, as Android's `LinkEditorEntry`: a saved link closes it through [onClose], a failed save goes to [say]
 * and the form stays. The address field starts empty: iOS asks before an app reads the clipboard, so the editor does
 * not read it unasked as Android's does.
 */
fun linkEditorViewController(
    key: AppRoutes.LinkEditor,
    say: (String) -> Unit,
    onClose: () -> Unit,
): UIViewController = linkSheetController {
    val scope = rememberCoroutineScope()
    LinkEditorSheetRoute(
        viewModel = hostedViewModel<LinkEditorViewModel>(*key.args.handleEntries(key.linkId)),
        onDone = onClose,
        onFailure = { error -> scope.say(error, say) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * One link's actions (`AppRoutes.LinkActions`) inside a SwiftUI sheet, as Android's `LinkActionsEntry`: votes, pins and
 * the confirmed delete stay here; the author's profile, `Мои баллы` and the editor go to [open], the next sheet or
 * screen in this one's place; the address goes to [copy] (the clipboard and its confirmation, the sheet stays), and a
 * link opens in the system, which closes the sheet ([linkFailed] keeps it). `Пожаловаться` shows the report dialog
 * over this sheet (the dialog stays Compose); an accepted report closes both.
 */
fun linkActionsViewController(
    key: AppRoutes.LinkActions,
    open: (AppRoute) -> Unit,
    copy: (String) -> Unit,
    linkFailed: () -> Unit,
    say: (String) -> Unit,
    onClose: () -> Unit,
): UIViewController {
    val platform = IosKoin.koin().get<PlatformActions>()
    val args = key.args
    return linkSheetController {
        val scope = rememberCoroutineScope()
        var reporting by remember { mutableStateOf<String?>(null) }
        val effects = remember {
            LinkActionsActions(
                onOpen = { link -> if (platform.openLink(link.url)) onClose() else linkFailed() },
                onAuthor = { isu -> UserScreenArgs.profileIsu(isu.toLong())?.let { open(AppRoutes.UserProfile(it)) } },
                onCopy = { link -> copy(link.url) },
                onScores = { link ->
                    val scores = SheetScoresArgs(
                        args.subjectId, args.subjectName, args.periodKey, link.url, SheetScoresArgs.Step.CONNECT,
                    )
                    open(AppRoutes.SheetScores(scores))
                },
                onEdit = { link -> open(AppRoutes.LinkEditor(args, link.id)) },
                onReport = { link -> reporting = link.id },
            )
        }
        LinkActionsSheetRoute(
            viewModel = hostedViewModel<SubjectLinksViewModel>(*args.handleEntries(key.linkId)),
            linkId = key.linkId,
            effects = effects,
            onDismiss = onClose,
            onFailure = { error -> scope.say(error, say) },
            modifier = Modifier.fillMaxWidth(),
        )
        reporting?.let { linkId ->
            ReportLinkHosted(args, linkId, onDone = onClose, onDismiss = { reporting = null })
        }
    }
}

/**
 * The report of a link, as Android's `ReportLinkEntry`, over the sheet it was asked from: a reason and an optional
 * comment on the kit's report dialog, which stays until the report is accepted ([onDone]); a failure shows under the
 * comment until the next send. Its own links view model, so the sheet under it does not take its answer.
 */
@Composable
private fun ReportLinkHosted(args: SubjectLinksArgs, linkId: String, onDone: () -> Unit, onDismiss: () -> Unit) {
    val viewModel = hostedViewModel<SubjectLinksViewModel>(*args.handleEntries(linkId))
    val state by viewModel.uiState.collectAsState()
    var failure by remember { mutableStateOf<AppError?>(null) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LinkEvent.Done, LinkEvent.Saved -> done()
                is LinkEvent.Failed -> failure = event.error
            }
        }
    }
    ReportLinkForm(
        sending = state.busy,
        error = failure,
        onSend = { reason, comment ->
            failure = null
            viewModel.report(linkId, reason, comment.trim().ifEmpty { null })
        },
        onDismiss = onDismiss,
    )
}

/** Android's `SubjectLinksArgs.toArguments(linkId)`: what every links view model reads from its handle. */
private fun SubjectLinksArgs.handleEntries(linkId: String? = null): Array<Pair<String, Any?>> = listOfNotNull(
    SubjectLinksArgs.SUBJECT_ID to subjectId,
    SubjectLinksArgs.SUBJECT_NAME to subjectName,
    SubjectLinksArgs.PERIOD_KEY to periodKey,
    linkId?.let { SubjectLinksArgs.LINK_ID to it },
).toTypedArray()

/** A failure as the text Android's snackbar shows, for the Swift banner over the sheet. */
private fun CoroutineScope.say(error: AppError, say: (String) -> Unit) {
    launch { say(error.toUiText().resolve()) }
}

/**
 * A links sheet's content in a see-through controller: the SwiftUI sheet's grouped background shows around the
 * `SheetScaffold`, which draws the same colour, and behind the actions sheet, which draws none. Only the sides and the
 * home indicator are kept clear: the sheet starts below the status bar, whose height Compose still reports.
 */
private fun linkSheetController(content: @Composable () -> Unit): UIViewController = screenController(opaque = false) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            ),
    ) {
        content()
    }
}
