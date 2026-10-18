package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The registry of settings page providers: one builder per page, one handler per row. */
class SettingsPagesTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    // Lazy: the fixture's ViewModel needs the main dispatcher setUp installs.
    private val pages by lazy { createFixture().pages }
    private val navigationRows = SettingsPage.entries.filter { it != SettingsPage.ROOT }.map(SettingRowId::navigation)
    /** «Подключение и данные» and «Уведомления» have view models of their own; no provider builds them. */
    private val ownScreens = setOf(SettingsPage.SERVICES, SettingsPage.PRIVACY, SettingsPage.NOTIFICATIONS)

    @Test
    fun everySettingsPageHasExactlyOneProviderAndTheOwnScreensNone() {
        SettingsPage.entries.forEach { page ->
            assertEquals(if (page in ownScreens) 0 else 1, pages.providers.count { page in it.pages }, "providers of $page")
        }
    }

    @Test
    fun everyRowHasExactlyOneHandlerAndNavigationRowsAreOpenedByTheScreen() {
        SettingRowId.entries.forEach { id ->
            val handlers = pages.providers.count { id in it.rows }
            if (id in navigationRows) {
                assertEquals(0, handlers, "handlers of navigation row $id")
                assertNull(pages.forRow(id))
            } else {
                assertEquals(1, handlers, "handlers of $id")
            }
        }
    }

    @Test
    fun aPageShowsOnlyItsProvidersRowsAndNavigationRows() {
        val states = listOf(
            SettingsPageState(
                local = LocalSettings(customServicesEnabled = true, barsMarksEnabled = true),
                notificationsGranted = false,
                hasCustomSpoiler = true,
                imageBusy = false,
                calendar = CalendarSyncState(enabled = true)
            ),
            SettingsPageState(
                local = LocalSettings(),
                notificationsGranted = null,
                hasCustomSpoiler = false,
                imageBusy = true
            )
        )
        (SettingsPage.entries - ownScreens).forEach { page ->
            val provider = pages.forPage(page)
            states.forEach { state ->
                val ids = provider.sections(page, state).flatMap(SettingSection::items).map { it.id }
                val foreign = ids.filter { it !in provider.rows && it !in navigationRows }
                assertTrue(foreign.isEmpty(), "$page shows rows of other providers: $foreign")
            }
        }
    }
}
