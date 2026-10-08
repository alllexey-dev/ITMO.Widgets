package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSelection
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookIosRequest
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookIosRoute
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPeriodOption
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPeriodSheetContent
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookSubjectIosRoute
import dev.alllexey.itmowidgets.feature.recordbook.ui.SheetScoresIosRoute
import dev.alllexey.itmowidgets.feature.recordbook.ui.recordbookPeriodOptions
import dev.alllexey.itmowidgets.feature.recordbook.ui.subject.RecordbookSubjectExits
import dev.alllexey.itmowidgets.ios.di.IosKoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import platform.UIKit.UIViewController

/**
 * A hosted recordbook page and the channel the Swift host answers it through: the period picker's choice
 * ([RecordbookIosRequest.SelectPeriod], the tab root only) and a completed BARS sign-in
 * ([RecordbookIosRequest.BarsSignedIn]), as Android's Fragment results. Requests sent before the first frame wait in
 * an unlimited buffer.
 */
class RecordbookPage internal constructor(
    val controller: UIViewController,
    private val requests: Channel<RecordbookIosRequest>,
) {
    fun send(request: RecordbookIosRequest) {
        requests.trySend(request)
    }

    /** The Swift BARS sign-in sheet finished with a stored session: the page loads again. */
    fun barsSignedIn() = send(RecordbookIosRequest.BarsSignedIn)
}

/**
 * The recordbook tab's root (`AppRoutes.TabRoot(RECORDBOOK)`), L12's `RecordbookRoute` as Android's
 * `RecordbookTabRoot` hosts it. The period button asks Swift for the picker ([pickPeriod], `AppRoutes.RecordbookPeriod`
 * of the shown programs with the shown period checked), whose answer comes back through [RecordbookPage.send]; a
 * subject opens its page through [open] only with valid arguments; the BARS sign-in is the Swift sheet ([barsLogin]).
 * The route draws its own top bar and no insets, so the content keeps clear of the bars on every side.
 */
fun recordbookRootPage(
    open: (AppRoute) -> Unit,
    pickPeriod: (AppRoutes.RecordbookPeriod) -> Unit,
    barsLogin: () -> Unit,
): RecordbookPage {
    val requests = Channel<RecordbookIosRequest>(Channel.UNLIMITED)
    val flow = requests.receiveAsFlow()
    val controller = screenController {
        RecordbookPadded {
            RecordbookIosRoute(
                requests = flow,
                onOpenPeriods = { programs, selection -> pickPeriod(recordbookPeriodRoute(programs, selection)) },
                onOpenSubject = { selection, subject ->
                    subjectArgs(selection, subject).validOrNull()?.let { open(AppRoutes.RecordbookSubject(it)) }
                },
                onBarsLogin = barsLogin,
            )
        }
    }
    return RecordbookPage(controller, requests)
}

/**
 * The subject page (`AppRoutes.RecordbookSubject`), as Android's `RecordbookSubjectEntry` hosts it: the sheet and LMS
 * links open in the system ([linkFailed] when nothing takes them, `link_open_failed`), `Мои баллы` and a teacher's
 * profile open through [open], the BARS sign-in is the Swift sheet ([barsLogin]). The links section shows only while
 * iOS offers subject links (`PlatformCapabilities.reviews`, IO-09f); until then the link keys are not on iOS.
 */
fun recordbookSubjectPage(
    args: RecordbookSubjectArgs,
    open: (AppRoute) -> Unit,
    barsLogin: () -> Unit,
    linkFailed: () -> Unit,
    onBack: () -> Unit,
): RecordbookPage {
    val koin = IosKoin.koin()
    val actions = koin.get<PlatformActions>()
    val linksEnabled = koin.get<PlatformCapabilities>().reviews
    val exits = RecordbookSubjectExits(
        onBack = onBack,
        onOpenLink = { url -> if (!actions.openLink(url)) linkFailed() },
        onLinkActions = { links, linkId -> open(AppRoutes.LinkActions(links, linkId)) },
        onAllLinks = { links -> open(AppRoutes.SubjectLinks(links)) },
        onAddLink = { links -> open(AppRoutes.LinkEditor(links)) },
        onOpenTeacher = { isu -> UserScreenArgs.profileIsu(isu.toLong())?.let { open(AppRoutes.UserProfile(it)) } },
        onOpenSheetScores = { scores -> open(AppRoutes.SheetScores(scores)) },
        onBarsLogin = barsLogin,
    )
    val requests = Channel<RecordbookIosRequest>(Channel.UNLIMITED)
    val flow = requests.receiveAsFlow()
    val controller = screenController {
        RecordbookPadded { RecordbookSubjectIosRoute(args, flow, exits, linksEnabled) }
    }
    return RecordbookPage(controller, requests)
}

/**
 * The period picker's content (`AppRoutes.RecordbookPeriod`) inside a SwiftUI sheet, as Android's
 * `RecordbookPeriodEntry`: a pick goes to [deliver] once and Swift closes the sheet.
 */
fun recordbookPeriodViewController(
    period: AppRoutes.RecordbookPeriod,
    deliver: (RecordbookIosRequest.SelectPeriod) -> Unit,
    onClose: () -> Unit,
): UIViewController = recordbookSheetController {
    RecordbookPeriodSheetContent(
        options = period.options(),
        programName = period.programName,
        selectedProgram = period.selectedProgram,
        selectedSemester = period.selectedSemester,
        onSelect = { option -> deliver(RecordbookIosRequest.SelectPeriod(option.programId, option.semester)) },
        onClose = onClose,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * `Мои баллы` (`AppRoutes.SheetScores`) inside a SwiftUI sheet, as Android's `SheetScoresEntry`: it closes through
 * [onClose] once the total is saved, and a failed save goes to [saveFailed] as its text for Swift to show.
 */
fun sheetScoresViewController(
    scores: SheetScoresArgs,
    onClose: () -> Unit,
    saveFailed: (String) -> Unit,
): UIViewController = recordbookSheetController {
    SheetScoresIosRoute(scores, onClose, saveFailed)
}

/** Android's `recordbookPeriodRoute`: the picker of [programs] with [selection] checked, one element per period. */
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

/** The routes draw their own top bar and no insets (DS-03), so the host pads the safe area on every side. */
@Composable
private fun RecordbookPadded(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        content()
    }
}

/**
 * A sheet's content in a see-through controller: the SwiftUI sheet's grouped background shows around the
 * `SheetScaffold`, which draws the same colour. Only the sides and the home indicator are kept clear: the sheet starts
 * below the status bar, whose height Compose still reports.
 */
private fun recordbookSheetController(content: @Composable () -> Unit): UIViewController =
    screenController(opaque = false) {
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
