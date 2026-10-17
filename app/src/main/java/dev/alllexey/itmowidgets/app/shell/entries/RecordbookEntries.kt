package dev.alllexey.itmowidgets.app.shell.entries

import android.app.Activity
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.ActivityRoutes
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.di.KoinStarter
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookTarget
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTab
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresEvent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookActions
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookRoute
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresActions
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresSheet
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.RecordbookSubjectActions
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.RecordbookSubjectRoute
import org.koin.core.parameter.parametersOf

/**
 * The recordbook's keys: the subject page and `Мои баллы`, both reading their arguments from the key.
 */
internal fun EntryRegistry.Builder.recordbookEntries() {
    entry<AppRoutes.RecordbookSubject>(args = { it.args.toBundle() }) { key, navigator ->
        RecordbookSubjectEntry(key, navigator)
    }
    entry<AppRoutes.SheetScores>(args = { it.args.toBundle() }) { key, navigator ->
        SheetScoresEntry(onClose = { navigator.close(key) })
    }
}

/**
 * The recordbook tab's root with its own root header (the period's subtitle and the period action): a subject opens
 * its page only with [RecordbookSubjectArgs.validOrNull] arguments, physical education the Спорт tab, `Подключить` and
 * `Войти` the BARS sign-in, after which the list loads again with BARS connected.
 */
@Composable
internal fun RecordbookTabRoot(navigator: Nav3AppNavigator) {
    val viewModel: RecordbookViewModel = entryViewModel()
    val barsLogin = rememberBarsLogin(onSignedIn = viewModel::barsSignedIn)
    val actions = remember(navigator, barsLogin) {
        RecordbookActions(
            onOpen = { target ->
                when (target) {
                    is RecordbookTarget.Subject ->
                        target.args.validOrNull()?.let { navigator.open(AppRoutes.RecordbookSubject(it)) }
                    RecordbookTarget.Sport -> navigator.select(AppTab.SPORT)
                }
            },
            onBarsSignIn = barsLogin,
        )
    }
    RecordbookRoute(UiText.Res(AppTab.RECORDBOOK.label).asString(), actions, viewModel)
}

/**
 * The subject page, on `Материалы` for a key with `materials` (the lesson sheet's links): links open outside the app,
 * the link sheets, `Мои баллы`, a teacher's profile and the review editor open above it, `Подключить` opens the
 * connection settings, and a completed BARS sign-in loads it again. A key whose arguments
 * [RecordbookSubjectArgs.validOrNull] refuses closes at once and leaves the recordbook root.
 */
@Composable
private fun RecordbookSubjectEntry(key: AppRoutes.RecordbookSubject, navigator: Nav3AppNavigator) {
    val args = key.args.validOrNull()
    if (args == null) {
        LaunchedEffect(key) { navigator.close(key) }
        return
    }
    val view = LocalView.current
    val platform = LocalPlatformActions.current
    val context = LocalContext.current
    val viewModel: RecordbookSubjectViewModel = viewModel {
        KoinStarter.ensureStarted(context).get<RecordbookSubjectViewModel> { parametersOf(args) }
            .also { if (key.materials) it.selectTab(SubjectTab.MATERIALS) }
    }
    val barsLogin = rememberBarsLogin(onSignedIn = viewModel::barsSignedIn)
    val actions = remember(key, navigator, view, platform, barsLogin) {
        RecordbookSubjectActions(
            onBack = { navigator.close(key) },
            onOpenLink = { url -> openLink(platform, view, url) },
            onLinkActions = { links, linkId -> navigator.open(AppRoutes.LinkActions(links, linkId)) },
            onAllLinks = { navigator.open(AppRoutes.SubjectLinks(it)) },
            onAddLink = { navigator.open(AppRoutes.LinkEditor(it)) },
            onOpenTeacher = navigator::openUserProfile,
            onWriteReview = { navigator.open(AppRoutes.ReviewEditor(it)) },
            onConnect = { navigator.open(AppRoutes.Settings(SERVICES_PAGE)) },
            onOpenSheetScores = { navigator.open(AppRoutes.SheetScores(it)) },
            onBarsSignIn = barsLogin,
            onOpenSport = { navigator.select(AppTab.SPORT) },
            onOpenLesson = { target ->
                when (target) {
                    is LessonTarget.Lesson -> navigator.open(AppRoutes.LessonDetails(target.args))
                    is LessonTarget.PendingSport -> navigator.open(AppRoutes.PendingSportDetails(target.args))
                }
            },
        )
    }
    RecordbookSubjectRoute(actions, viewModel)
}

/** The settings page of `Подключение к ITMO.Widgets` (`SettingsPage.SERVICES`). */
private const val SERVICES_PAGE = "SERVICES"

/**
 * `Мои баллы`: the sheet closes itself once the ViewModel is done and shows a failed save in a snackbar.
 */
@Composable
private fun SheetScoresEntry(onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SheetScoresViewModel = entryViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(state) {
        if (state == SheetScoresUiState.Done) currentOnClose()
    }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is SheetScoresEvent.SaveFailed ->
                        Snackbar.make(view, event.text.resolve(context), Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    val actions = remember(viewModel) {
        SheetScoresActions(
            onRetry = { viewModel.refresh(RefreshMode.Force) },
            onPickRow = viewModel::pickRow,
            onPickTab = viewModel::pickTab,
            onPickTotal = viewModel::pickTotal,
            onResetTotal = viewModel::resetTotal,
        )
    }
    SheetScoresSheet(
        subjectName = viewModel.scope.subjectName,
        state = state,
        actions = actions,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Starts the BARS sign-in in `BarsLoginActivity` for a result; [onSignedIn] runs after a completed one. */
@Composable
private fun rememberBarsLogin(onSignedIn: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnSignedIn by rememberUpdatedState(onSignedIn)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) currentOnSignedIn()
    }
    return remember(launcher, context) { { launcher.launch(ActivityRoutes.barsLogin(context)) } }
}

/** An https link outside the app (t.me in Telegram); without a handler a snackbar says so, as `openLink` does. */
private fun openLink(platform: PlatformActions, anchor: View, url: String) {
    if (!platform.openLink(url)) Snackbar.make(anchor, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
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
