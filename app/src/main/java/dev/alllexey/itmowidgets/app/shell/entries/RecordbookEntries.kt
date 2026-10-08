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
import androidx.navigation3.runtime.result.LocalResultEventBus
import androidx.navigation3.runtime.result.ResultEffect
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.shell.ActivityRoutes
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.toBundle
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.designsystem.host.LocalPlatformActions
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSelection
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresEvent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPeriodOption
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPeriodSheetContent
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookRoute
import dev.alllexey.itmowidgets.feature.recordbook.ui.recordbookPeriodOptions
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresActions
import dev.alllexey.itmowidgets.feature.recordbook.ui.sheets.SheetScoresSheet
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.RecordbookSubjectExits
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.RecordbookSubjectRoute
import org.koin.core.parameter.parametersOf

/**
 * The recordbook's keys: the subject page, the period picker and `Мои баллы`. The subject page and the sheet read the
 * arguments their Fragment hosts read; the period picker answers the tab root with a [RecordbookPeriodChoice].
 */
internal fun EntryRegistry.Builder.recordbookEntries() {
    entry<AppRoutes.RecordbookSubject>(args = { it.args.toBundle() }) { key, navigator ->
        RecordbookSubjectEntry(key, navigator)
    }
    entry<AppRoutes.RecordbookPeriod> { key, navigator ->
        RecordbookPeriodEntry(key, onClose = { navigator.close(key) })
    }
    entry<AppRoutes.SheetScores>(args = { it.args.toBundle() }) { key, navigator ->
        SheetScoresEntry(onClose = { navigator.close(key) })
    }
}

/**
 * The recordbook tab's root: `RecordbookRoute` with the tab's ViewModel. It opens the period picker and takes the
 * picked period back once, opens a subject only with [RecordbookSubjectArgs.validOrNull] arguments and refreshes after
 * a completed BARS sign-in, as `RecordbookFragment` does.
 */
@Composable
internal fun RecordbookTabRoot(navigator: Nav3AppNavigator) {
    val viewModel: RecordbookViewModel = entryViewModel()
    val barsLogin = rememberBarsLogin(onSignedIn = { viewModel.refresh(RefreshMode.Force) })
    ResultEffect<RecordbookPeriodChoice> { viewModel.selectPeriod(it.programId, it.semester) }
    RecordbookRoute(
        onOpenPeriods = { programs, selection -> navigator.open(recordbookPeriodRoute(programs, selection)) },
        onOpenSubject = { selection, subject ->
            subjectArgs(selection, subject).validOrNull()?.let { navigator.open(AppRoutes.RecordbookSubject(it)) }
        },
        onBarsLogin = barsLogin,
        viewModel = viewModel,
    )
}

/** The period the picker answers with; the tab root is its only opener. */
internal data class RecordbookPeriodChoice(val programId: Long, val semester: Int)

/** The picker of [programs] with [selection] checked; the lists of the key are parallel, one element per period. */
internal fun recordbookPeriodRoute(
    programs: List<RecordbookProgram>,
    selection: RecordbookSelection,
): AppRoutes.RecordbookPeriod {
    val options = recordbookPeriodOptions(programs)
    return AppRoutes.RecordbookPeriod(
        programName = selection.program.name,
        programNames = options.map { it.programName },
        programIds = options.map { it.programId },
        semesters = options.map { it.semester },
        courses = options.map { it.course },
        years = options.map { it.studyYear },
        actual = options.map { it.actual },
        selectedProgram = selection.program.id,
        selectedSemester = selection.period.semester,
    )
}

private fun AppRoutes.RecordbookPeriod.options(): List<RecordbookPeriodOption> = semesters.indices.map { index ->
    RecordbookPeriodOption(
        programId = programIds[index],
        semester = semesters[index],
        course = courses[index],
        studyYear = years[index],
        actual = actual[index],
        programName = programNames.getOrElse(index) { "" },
    )
}

private fun subjectArgs(selection: RecordbookSelection, subject: RecordbookSubject) = RecordbookSubjectArgs(
    entryId = subject.entryId,
    programId = selection.program.id,
    semester = selection.period.semester,
    studyYear = selection.period.studyYear,
    barsPlan = subject.barsJournal?.planId,
    barsType = subject.barsJournal?.type,
    barsIdentifier = subject.barsJournal?.identifier,
)

/** The period picker: a pick answers the tab root and closes the sheet, as `RecordbookPeriodBottomSheet` does. */
@Composable
private fun RecordbookPeriodEntry(key: AppRoutes.RecordbookPeriod, onClose: () -> Unit) {
    val results = LocalResultEventBus.current
    val options = remember(key) { key.options() }
    RecordbookPeriodSheetContent(
        options = options,
        programName = key.programName,
        selectedProgram = key.selectedProgram,
        selectedSemester = key.selectedSemester,
        onSelect = { option ->
            results.sendResult(RecordbookPeriodChoice(option.programId, option.semester))
            onClose()
        },
        onClose = onClose,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The subject page: links open outside the app, the link sheets, `Мои баллы` and a teacher's profile open above it,
 * and a completed BARS sign-in refreshes it. A key whose arguments [RecordbookSubjectArgs.validOrNull] refuses closes
 * at once and leaves the recordbook root.
 */
@Composable
private fun RecordbookSubjectEntry(key: AppRoutes.RecordbookSubject, navigator: Nav3AppNavigator) {
    if (key.args.validOrNull() == null) {
        LaunchedEffect(key) { navigator.close(key) }
        return
    }
    val view = LocalView.current
    val platform = LocalPlatformActions.current
    val viewModel: RecordbookSubjectViewModel = entryViewModel()
    val barsLogin = rememberBarsLogin(onSignedIn = { viewModel.refresh(RefreshMode.Force) })
    val exits = remember(key, navigator, view, platform, barsLogin) {
        RecordbookSubjectExits(
            onBack = { navigator.close(key) },
            onOpenLink = { url -> openLink(platform, view, url) },
            onLinkActions = { args, linkId -> navigator.open(AppRoutes.LinkActions(args, linkId)) },
            onAllLinks = { navigator.open(AppRoutes.SubjectLinks(it)) },
            onAddLink = { navigator.open(AppRoutes.LinkEditor(it)) },
            onOpenTeacher = navigator::openUserProfile,
            onOpenSheetScores = { navigator.open(AppRoutes.SheetScores(it)) },
            onBarsLogin = barsLogin,
        )
    }
    RecordbookSubjectRoute(key.args.semester, exits, viewModel)
}

/**
 * `Мои баллы`: the sheet closes itself once the ViewModel is done and shows a failed save in a snackbar, as
 * `SheetScoresBottomSheet` does.
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
