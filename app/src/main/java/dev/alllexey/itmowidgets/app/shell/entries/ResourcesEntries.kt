package dev.alllexey.itmowidgets.app.shell.entries

import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsActions
import dev.alllexey.itmowidgets.feature.resources.ui.LinkActionsSheetRoute
import dev.alllexey.itmowidgets.feature.resources.ui.LinkEditorSheetRoute
import dev.alllexey.itmowidgets.feature.resources.ui.ReportLinkForm
import dev.alllexey.itmowidgets.feature.resources.ui.SubjectLinksSheetRoute
import dev.alllexey.itmowidgets.feature.resources.ui.copySubjectLink
import dev.alllexey.itmowidgets.feature.resources.ui.pasteClipboardLink
import dev.alllexey.itmowidgets.feature.resources.ui.toArguments
import kotlinx.coroutines.flow.first
import org.koin.core.parameter.parametersOf

/**
 * The resources keys (route map S3-S5, D2): a subject period's links, the link editor, one link's actions and the
 * report. Each reads the arguments the Fragment sheets read under `SubjectLinksArgs`; a sheet that opens another
 * sheet, a screen or the report closes itself first.
 */
internal fun EntryRegistry.Builder.resourcesEntries() {
    entry<AppRoutes.SubjectLinks>(args = { it.args.toArguments() }) { key, navigator ->
        SubjectLinksEntry(key.args, onClose = { navigator.close(key) }, navigator)
    }
    entry<AppRoutes.LinkEditor>(args = { it.args.toArguments(it.linkId) }) { key, navigator ->
        LinkEditorEntry(onDone = { navigator.close(key) })
    }
    entry<AppRoutes.LinkActions>(args = { it.args.toArguments(it.linkId) }) { key, navigator ->
        LinkActionsEntry(key, navigator)
    }
    entry<AppRoutes.ReportLink>(args = { it.args.toArguments(it.linkId) }) { key, navigator ->
        ReportLinkEntry(key.linkId, onClose = { navigator.close(key) })
    }
}

/** All links of one subject period (S3): a link opens outside the app, its actions and the editor in sheets. */
@Composable
private fun SubjectLinksEntry(args: SubjectLinksArgs, onClose: () -> Unit, navigator: Nav3AppNavigator) {
    val view = LocalView.current
    val platform = LocalPlatformActions.current
    SubjectLinksSheetRoute(
        viewModel = entryViewModel(),
        onOpen = { link -> openLink(platform, view, link.url) },
        onLinkActions = { link -> navigator.open(AppRoutes.LinkActions(args, link.id)) },
        onAdd = { navigator.open(AppRoutes.LinkEditor(args)) },
        onClose = onClose,
        onFailure = { error -> showFailure(view, error) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The link editor (S4): a new link's sheet starts with the clipboard link once its window first has focus, only on
 * the sheet's first creation, as `LinkEditorBottomSheet` does; a saved link closes it.
 */
@Composable
private fun LinkEditorEntry(onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalWindowInfo.current
    val viewModel: LinkEditorViewModel = entryViewModel()
    var firstCreation by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(viewModel) {
        if (!firstCreation) return@LaunchedEffect
        firstCreation = false
        if (viewModel.uiState.value.editing) return@LaunchedEffect
        snapshotFlow { window.isWindowFocused }.first { it }
        viewModel.pasteClipboardLink(context)
    }
    LinkEditorSheetRoute(
        viewModel = viewModel,
        onDone = onDone,
        onFailure = { error -> showFailure(view, error) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * One link's actions (S5): a vote keeps the sheet; every other action closes it before the link, the profile, the
 * sheet scores, the editor or the report opens.
 */
@Composable
private fun LinkActionsEntry(key: AppRoutes.LinkActions, navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val view = LocalView.current
    val platform = LocalPlatformActions.current
    val close = { navigator.close(key) }
    val effects = remember(key, navigator, context, view, platform) {
        linkActions(
            key.args,
            navigator,
            close,
            onOpen = { url -> openLink(platform, view, url) },
            onCopy = { url -> context.copySubjectLink(url) },
        )
    }
    LinkActionsSheetRoute(
        viewModel = entryViewModel(),
        linkId = key.linkId,
        effects = effects,
        onDismiss = close,
        onFailure = { error -> showFailure(view, error) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** [onCopy] is false when nothing was copied, and the sheet then stays; otherwise it closes, confirmed or not. */
private fun linkActions(
    args: SubjectLinksArgs,
    navigator: Nav3AppNavigator,
    close: () -> Unit,
    onOpen: (url: String) -> Unit,
    onCopy: (url: String) -> Boolean,
) = LinkActionsActions(
    onOpen = { link ->
        onOpen(link.url)
        close()
    },
    onAuthor = { isu ->
        close()
        navigator.openUserProfile(isu)
    },
    onCopy = { link -> if (onCopy(link.url)) close() },
    onScores = { link ->
        close()
        val scores = SheetScoresArgs(
            args.subjectId, args.subjectName, args.periodKey, link.url, SheetScoresArgs.Step.CONNECT,
        )
        navigator.open(AppRoutes.SheetScores(scores))
    },
    onEdit = { link ->
        close()
        navigator.open(AppRoutes.LinkEditor(args, link.id))
    },
    onReport = { link ->
        close()
        navigator.open(AppRoutes.ReportLink(args, link.id))
    },
)

/**
 * The report of a link (D2): a reason and an optional comment on the kit's report dialog, which stays until the
 * report is accepted; a failure shows under the comment until the next send.
 */
@Composable
private fun ReportLinkEntry(linkId: String, onClose: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SubjectLinksViewModel = entryViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    LinkEvent.Done, LinkEvent.Saved -> onClose()
                    is LinkEvent.Failed -> failure = event.error
                }
            }
        }
    }
    DialogSceneAnchor()
    ReportLinkForm(
        sending = state.busy,
        error = failure,
        onSend = { reason, comment ->
            failure = null
            viewModel.report(linkId, reason, comment.trim().ifEmpty { null })
        },
        onDismiss = onClose,
    )
}

/**
 * The kit's report dialogs bring their own window, whose Back and tap outside close them only while nothing is being
 * sent; the Navigation 3 dialog scene's window under them only anchors it, transparent, undimmed and letting touches
 * through, as the Fragment hosts' own dialog does.
 */
@Composable
internal fun DialogSceneAnchor() {
    val view = LocalView.current
    SideEffect {
        val window = ((view as? DialogWindowProvider) ?: (view.parent as? DialogWindowProvider))?.window
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }
}

/** An https link outside the app (t.me in Telegram); without a handler a snackbar says so, as `openLink` does. */
private fun openLink(platform: PlatformActions, anchor: View, url: String) {
    if (!platform.openLink(url)) Snackbar.make(anchor, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
}

private fun showFailure(anchor: View, error: AppError) {
    Snackbar.make(anchor, error.messageRes(), Snackbar.LENGTH_SHORT).show()
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
