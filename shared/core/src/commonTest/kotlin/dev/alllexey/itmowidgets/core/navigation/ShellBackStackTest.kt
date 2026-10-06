package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The model-level parts of `MainNavigationTest`'s 9 scenarios, then the sheet, request and entry rules. */
class ShellBackStackTest {
    private val json = Json { serializersModule = appRouteSerializersModule() }

    @Test
    fun nestedSettingsNeverReplaceTheTabAndBackUncoversTheSameProfile() {
        val profile = ShellBackStack().select(AppTab.ME)
        val nested = profile.open(AppRoutes.Settings()).open(AppRoutes.Settings("PRIVACY"))

        assertEquals(AppTab.ME, nested.tab)
        assertEquals(
            listOf(AppRoutes.TabRoot(AppTab.ME), AppRoutes.Settings(), AppRoutes.Settings("PRIVACY")),
            nested.entries
        )
        assertTrue(nested.coversBar)
        val oneBack = nested.back()!!
        assertEquals(listOf(AppRoutes.Settings()), oneBack.overlays)
        assertEquals(profile, oneBack.back())
    }

    @Test
    fun everyTabChangeAndReselectionDiscardsTheWholeContextualHistory() {
        AppTab.entries.forEach { tab ->
            listOf(tab, AppTab.HOME, AppTab.ME).forEach { from ->
                val deep = ShellBackStack().select(from)
                    .open(AppRoutes.Friends)
                    .open(AppRoutes.UserProfile(100001))
                    .open(AppRoutes.ReportReview(RouteSamples.teacher, "review-1"))

                val selected = deep.select(tab)

                assertEquals(ShellBackStack(tab = tab), selected, "$from -> $tab")
            }
        }
    }

    @Test
    fun recreationRestoresTheOverlayDepthButTheNextTabSelectionDoesNot() {
        val deep = ShellBackStack().select(AppTab.ME).open(AppRoutes.Settings()).open(AppRoutes.Diagnostics)

        val restored = json.roundTrip(deep)

        assertEquals(listOf(AppRoutes.Settings(), AppRoutes.Diagnostics), restored.overlays)
        assertTrue(restored.select(AppTab.ME).overlays.isEmpty())
    }

    @Test
    fun theDebugSectionUsesTheSameOverlayAndAGateKeyClosesNothing() {
        val debug = ShellBackStack().select(AppTab.ME).open(AppRoutes.DebugTools)

        assertEquals(listOf(AppRoutes.DebugTools), debug.overlays)
        assertSame(debug, debug.open(AppRoutes.Auth))
        assertSame(debug, debug.open(AppRoutes.Onboarding))
    }

    @Test
    fun aTabChangeRightAfterAnOpenLeavesNothingAboveTheBar() {
        val opened = ShellBackStack().select(AppTab.ME).open(AppRoutes.Settings())

        assertFalse(opened.select(AppTab.SCHEDULE).coversBar)
        assertFalse(opened.open(AppRoutes.TabRoot(AppTab.SPORT)).coversBar)
    }

    @Test
    fun everyTabKeepsItsOwnStackAndRequestAcrossSwitchesAndRecreation() {
        var stack = ShellBackStack().request(TabRequest.SportLesson(42))
        AppTab.entries.forEach { tab -> stack = stack.select(tab) }
        val restored = json.roundTrip(stack)

        AppTab.entries.forEach { tab -> assertEquals(listOf(AppRoutes.TabRoot(tab)), restored.tabStack(tab)) }
        assertEquals(AppTab.ME, restored.tab)
        assertEquals(TabRequest.SportLesson(42), restored.pendingRequest(AppTab.SPORT))
    }

    @Test
    fun reselectingTheTabClosesItsSheetAndKeepsTheTab() {
        val picker = ShellBackStack().select(AppTab.SCHEDULE).open(AppRoutes.FriendSelector())

        assertEquals(listOf(AppRoutes.FriendSelector()), picker.floating)
        assertEquals(ShellBackStack(tab = AppTab.SCHEDULE), picker.select(AppTab.SCHEDULE))
    }

    @Test
    fun everyFullScreenKeyCoversTheBarAndSheetsAndDialogsDoNot() {
        RouteSamples.ofKind(RouteKind.SCREEN).forEach { screen ->
            assertTrue(ShellBackStack().open(screen).coversBar, "$screen")
        }
        (RouteSamples.ofKind(RouteKind.SHEET) + RouteSamples.ofKind(RouteKind.DIALOG)).forEach { floating ->
            val opened = ShellBackStack().open(floating)
            assertFalse(opened.coversBar, "$floating")
            assertEquals(listOf(floating), opened.floating)
        }
    }

    @Test
    fun backFromAnotherTabReturnsHomeAndBackFromHomeLeaves() {
        val schedule = ShellBackStack().select(AppTab.SCHEDULE)

        val home = schedule.back()!!

        assertEquals(ShellBackStack(tab = AppTab.HOME), home)
        assertNull(home.back())
    }

    @Test
    fun backClosesTheTopSheetThenTheOverlayThenTheTab() {
        val stack = ShellBackStack().select(AppTab.RECORDBOOK)
            .open(AppRoutes.RecordbookSubject(RouteSamples.subject))
            .open(AppRoutes.SubjectLinks(RouteSamples.links))
            .open(AppRoutes.LinkEditor(RouteSamples.links))

        val steps = generateSequence(stack) { it.back() }.toList()

        assertEquals(
            listOf(
                AppRoutes.LinkEditor(RouteSamples.links),
                AppRoutes.SubjectLinks(RouteSamples.links),
                AppRoutes.RecordbookSubject(RouteSamples.subject),
                AppRoutes.TabRoot(AppTab.RECORDBOOK),
                AppRoutes.TabRoot(AppTab.HOME)
            ),
            steps.map { it.entries.last() }
        )
    }

    @Test
    fun aScreenOpenedFromASheetClosesTheSheetFirst() {
        val sheet = ShellBackStack().select(AppTab.SCHEDULE)
            .open(AppRoutes.LessonDetails(RouteSamples.lesson))

        val profile = sheet.open(AppRoutes.UserProfile(100002))

        assertEquals(emptyList(), profile.floating)
        assertEquals(listOf(AppRoutes.UserProfile(100002)), profile.overlays)
        assertEquals(ShellBackStack(tab = AppTab.SCHEDULE), profile.back())
    }

    @Test
    fun aSheetOpensOverASheetButNeverTwiceAndTheSportPairIsExclusive() {
        val links = ShellBackStack().open(AppRoutes.SubjectLinks(RouteSamples.links))
        val editor = links.open(AppRoutes.LinkEditor(RouteSamples.links))

        assertEquals(2, editor.floating.size)
        assertSame(editor, editor.open(AppRoutes.LinkEditor(RouteSamples.links, linkId = "link-2")))
        assertSame(editor, editor.open(AppRoutes.SubjectLinks(RouteSamples.links.copy(subjectId = 6))))

        val pending = ShellBackStack().open(AppRoutes.PendingSportDetails(RouteSamples.pendingSport))
        assertSame(pending, pending.open(SportPartner))
        val partner = ShellBackStack().open(SportPartner)
        assertSame(partner, partner.open(AppRoutes.PendingSportDetails(RouteSamples.pendingSport)))
        assertEquals(2, partner.open(AppRoutes.LessonDetails(RouteSamples.lesson)).floating.size)
    }

    @Test
    fun aKeyThatClosesItselfLeavesTheRestInPlace() {
        val stack = ShellBackStack().select(AppTab.ME)
            .open(AppRoutes.UserProfile(100001))
            .open(AppRoutes.SubjectLinks(RouteSamples.links))
            .open(AppRoutes.LinkActions(RouteSamples.links, "link-1"))

        val withoutLinks = stack.close(AppRoutes.SubjectLinks(RouteSamples.links))

        assertEquals(listOf(AppRoutes.LinkActions(RouteSamples.links, "link-1")), withoutLinks.floating)
        assertEquals(stack.overlays, withoutLinks.overlays)
        assertEquals(ShellBackStack(tab = AppTab.ME), stack.close(AppRoutes.UserProfile(100001)))
        assertSame(stack, stack.close(AppRoutes.QrPass))
        assertEquals(ShellBackStack(tab = AppTab.ME), stack.dismissOverlays())
    }

    @Test
    fun aTabRequestIsConsumedOnceAndANewerOneReplacesIt() {
        val requested = ShellBackStack()
            .request(TabRequest.SportLesson(41))
            .request(TabRequest.SportLesson(42, predicted = true))
            .request(TabRequest.ScheduleToday)

        assertEquals(TabRequest.SportLesson(42, predicted = true), requested.pendingRequest(AppTab.SPORT))
        assertEquals(TabRequest.ScheduleToday, requested.pendingRequest(AppTab.SCHEDULE))
        assertNull(requested.pendingRequest(AppTab.HOME))
        val consumed = requested.consume(AppTab.SPORT)
        assertNull(consumed.pendingRequest(AppTab.SPORT))
        assertEquals(TabRequest.ScheduleToday, consumed.pendingRequest(AppTab.SCHEDULE))
        assertEquals(consumed.requests, consumed.select(AppTab.HOME).open(AppRoutes.QrPass).back()!!.requests)
    }

    @Test
    fun entryRoutesOpenTheirLayersAboveAFreshTab() {
        val busy = ShellBackStack().select(AppTab.ME).open(AppRoutes.Settings()).open(AppRoutes.IcsExport)

        fun run(action: String, isu: Int? = null, link: String? = null) =
            busy.apply(EntryRouteParser.parse(action, isu = isu, link = link)!!)

        assertEquals(
            ShellBackStack(tab = AppTab.HOME, overlays = listOf(AppRoutes.QrPass)),
            run(AppEntryIntents.ACTION_OPEN_QR_PASS)
        )
        assertEquals(
            ShellBackStack(tab = AppTab.SCHEDULE, requests = mapOf(AppTab.SCHEDULE to TabRequest.ScheduleToday)),
            run(AppEntryIntents.ACTION_OPEN_TODAY)
        )
        assertEquals(
            ShellBackStack(tab = AppTab.ME, overlays = listOf(AppRoutes.UserProfile(100001))),
            run(EntryRouteParser.ACTION_VIEW, link = "https://widgets.alllexey.dev/u/100001")
        )
        assertEquals(
            ShellBackStack(tab = AppTab.HOME, floating = listOf(AppRoutes.LinkUnavailable)),
            run(EntryRouteParser.ACTION_VIEW, link = "https://widgets.alllexey.dev/u/abc")
        )
        assertEquals(ShellBackStack(tab = AppTab.RECORDBOOK), run(AppEntryIntents.ACTION_OPEN_BARS_LOGIN))
    }

    private fun Json.roundTrip(stack: ShellBackStack): ShellBackStack =
        decodeFromString(ShellBackStack.serializer(), encodeToString(ShellBackStack.serializer(), stack))

    /** Stands for the sport tab's details sheet, a feature-typed key of the same exclusive group. */
    private data object SportPartner : AppRoute {
        override val kind get() = RouteKind.SHEET
        override val exclusiveGroup get() = AppRoutes.SPORT_DETAILS_GROUP
    }
}
