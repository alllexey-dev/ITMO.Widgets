package dev.alllexey.itmowidgets.feature.sport.data.bell

import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignNotice
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.fakeFileSystemOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

class SportBellSourceTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock(Instant.parse("2026-09-15T10:00:00Z"))
    private val files = fakeFileSystemOf()
    private val path = "/no_backup/sport_results.json".toPath()

    private fun log() = SportResultLog(AtomicTextFile(path, files), clock, AppDispatchers(dispatcher, dispatcher, dispatcher))

    @Test
    fun aQueueResultIsARowLeadingToSportAndOutlivesTheProcess() = runTest(dispatcher) {
        val start = Instant.parse("2026-09-17T12:20:00Z")
        log().add(SportSignNotice(42, "Волейбол", start, booked = true))
        log().add(SportSignNotice(43, "Плавание", start, booked = false))

        val items = SportBellSource(log()).observe().first()

        assertEquals(listOf("sport:43:${clock.now().toEpochMilliseconds()}", "sport:42:${clock.now().toEpochMilliseconds()}"), items.map { it.id })
        assertEquals(BellKind.SPORT, items[0].kind)
        assertEquals(UiText.Res(SportStrings.sport_bell_refused, listOf("Плавание")), items[0].title)
        assertEquals(true, items[0].failed)
        assertEquals(UiText.Res(SportStrings.sport_bell_booked, listOf("Волейбол")), items[1].title)
        assertEquals(UiText.Res(SportStrings.sport_bell_lesson, listOf("чт, 17 сентября, 15:20")), items[1].text)
        assertEquals(EntryRoute(AppTab.SPORT), items[1].target)
    }

    @Test
    fun oldResultsExpireAndSignOutForgetsThem() = runTest(dispatcher) {
        val log = log()
        log.add(SportSignNotice(42, "Волейбол", clock.now(), booked = true))
        clock.advanceBy(31.days)
        log.add(SportSignNotice(43, "Плавание", clock.now(), booked = true))
        assertEquals(listOf(43L), log.observe().first().map { it.lessonId })

        log.clearSessionData()
        assertTrue(log().observe().first().isEmpty())
    }
}
