package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.ActivityRoute
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.EntryRouteParser
import dev.alllexey.itmowidgets.core.navigation.EntryShortcuts
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The legacy `MainActivityIntentRouting` and the shell's `EntryRouteParser` agree on every entry intent, until
 * SH-1d1 deletes the legacy one: each legacy route is translated into the shell's terms and must equal the new one.
 */
class EntryRouteParityTest {

    @Test
    fun bothParsersAgreeOnEveryActionLinkArgumentAndHistoryFlag() {
        var compared = 0
        for (action in ACTIONS) for (isu in ISUS) for (subject in SUBJECTS) for (link in LINKS) {
            for (history in listOf(false, true)) {
                val legacy = MainActivityIntentRouting.parse(action, isu, subject, history, link)
                val shell = EntryRouteParser.parse(action, isu, subject, history, link)
                val case = "$action isu=$isu subject=$subject link=$link history=$history"
                assertEquals(case, legacy?.toEntryRoute(), shell)
                compared++
            }
        }
        assertEquals(ACTIONS.size * ISUS.size * SUBJECTS.size * LINKS.size * 2, compared)
    }

    @Test
    fun everyTabIsOneBarItemInBarOrder() {
        assertEquals(MainNavigationCoordinator.ROOTS, TAB_IDS.values.toSet())
        assertEquals(AppRoot.entries.map { it.name }, AppTab.entries.map { it.name })
        assertEquals(barItemIds(), AppTab.entries.map { tab -> TAB_IDS.getValue(tab) })
    }

    @Test
    fun theShortcutIdsAreTheLaunchersOnes() {
        assertEquals(AppShortcuts.QR_PASS, EntryShortcuts.QR_PASS)
        assertEquals(AppShortcuts.TODAY, EntryShortcuts.TODAY)
        assertEquals("android.intent.action.VIEW", EntryRouteParser.ACTION_VIEW)
    }

    /** The legacy route in the shell's terms; a legacy field the shell cannot express fails the test. */
    private fun MainActivityRoute.toEntryRoute(): EntryRoute {
        val screenKey = when (screen) {
            null -> null
            AppScreen.QR_PASS -> AppRoutes.QrPass
            AppScreen.SCHEDULE_CHANGES -> AppRoutes.ScheduleChanges
            AppScreen.RECORDBOOK_SUBJECT -> AppRoutes.RecordbookSubject(subject!!)
            else -> error("no entry route opens $screen")
        }
        val profileKey = userIsu?.let(AppRoutes::UserProfile)
        assertTrue("a route opens one overlay: $this", screenKey == null || profileKey == null)
        val request = when {
            today -> TabRequest.ScheduleToday
            sportLessonId != null -> TabRequest.SportLesson(sportLessonId, sportLessonPredicted)
            else -> null
        }
        return EntryRoute(
            tab = TAB_IDS.entries.single { it.value == rootDestination }.key,
            overlay = screenKey ?: profileKey,
            request = request,
            activity = ActivityRoute.BARS_LOGIN.takeIf { barsLogin },
            alert = AppRoutes.LinkUnavailable.takeIf { linkUnavailable },
            shortcutId = shortcutId()
        )
    }

    /** The item ids of `res/menu/bottom_nav.xml`, top to bottom. */
    private fun barItemIds(): List<Int> {
        val menu = listOf("src/main/res/menu/bottom_nav.xml", "app/src/main/res/menu/bottom_nav.xml")
            .map(::File)
            .first(File::isFile)
        val items = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(menu).getElementsByTagName("item")
        return (0 until items.length).map { index ->
            val name = items.item(index).attributes.getNamedItem("android:id").nodeValue.substringAfter("@+id/")
            R.id::class.java.getField(name).getInt(null)
        }
    }

    private companion object {
        val TAB_IDS = mapOf(
            AppTab.RECORDBOOK to R.id.navigation_recordbook,
            AppTab.SCHEDULE to R.id.navigation_schedule,
            AppTab.HOME to R.id.navigation_home,
            AppTab.SPORT to R.id.navigation_sport,
            AppTab.ME to R.id.navigation_me
        )

        val ACTIONS: List<String?> = listOf(
            null,
            "",
            "unknown",
            EntryRouteParser.ACTION_VIEW,
            AppEntryIntents.ACTION_OPEN_SCHEDULE,
            AppEntryIntents.ACTION_OPEN_SPORT,
            AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES,
            AppEntryIntents.ACTION_OPEN_USER_PROFILE,
            AppEntryIntents.ACTION_OPEN_RECORDBOOK,
            AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT,
            AppEntryIntents.ACTION_OPEN_BARS_LOGIN,
            AppEntryIntents.ACTION_OPEN_QR_PASS,
            AppEntryIntents.ACTION_OPEN_TODAY
        )

        val ISUS: List<Int?> = listOf(null, Int.MIN_VALUE, -1, 0, 1, 123456, Int.MAX_VALUE)

        private val SUBJECT = RecordbookSubjectArgs(11, 1, 3, "2026/2027", 7, "flow", "7")
        val SUBJECTS: List<RecordbookSubjectArgs?> = listOf(
            null,
            SUBJECT,
            SUBJECT.copy(barsPlan = null, barsType = null, barsIdentifier = null),
            SUBJECT.copy(entryId = 0),
            SUBJECT.copy(programId = -1),
            SUBJECT.copy(semester = 0),
            SUBJECT.copy(studyYear = "2026"),
            SUBJECT.copy(barsType = null)
        )

        val LINKS: List<String?> = listOf(
            null,
            "",
            "not a link",
            "https://widgets.alllexey.dev/u/100001",
            "https://dev.widgets.alllexey.dev/u/100001/",
            "https://WIDGETS.alllexey.dev/u/5?utm_source=tg#top",
            "https://widgets.alllexey.dev/u/0",
            "https://widgets.alllexey.dev/u/-5",
            "https://widgets.alllexey.dev/u/abc",
            "https://widgets.alllexey.dev/u/99999999999",
            "https://widgets.alllexey.dev/u/",
            "https://widgets.alllexey.dev/u/1//",
            "https://widgets.alllexey.dev/sport/42",
            "https://widgets.alllexey.dev/sport/42/",
            "https://widgets.alllexey.dev/sport/p/42",
            "https://dev.widgets.alllexey.dev/sport/p/42/",
            "https://widgets.alllexey.dev/sport/abc",
            "https://widgets.alllexey.dev/sport/p/",
            "https://widgets.alllexey.dev/sport/p/x",
            "https://widgets.alllexey.dev/sport/99999999999999999999",
            "https://widgets.alllexey.dev/",
            "https://widgets.alllexey.dev/app/login?code=123456",
            "http://widgets.alllexey.dev/u/1",
            "https://example.com/u/1",
            "https://widgets.alllexey.dev.example.com/u/1",
            "intent://widgets.alllexey.dev/u/1"
        )
    }
}
