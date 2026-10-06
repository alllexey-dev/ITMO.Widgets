package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
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
    /** Shown by the schedule and recordbook pages, handled by the schedule provider. */
    private val sharedRows = setOf(SettingRowId.BACKGROUND_WORK)

    @Test
    fun everySettingsPageHasExactlyOneProvider() {
        SettingsPage.entries.forEach { page ->
            assertEquals(1, pages.providers.count { page in it.pages }, "providers of $page")
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
    fun aPageShowsOnlyItsProvidersRowsNavigationRowsAndSharedRows() {
        val states = listOf(
            SettingsPageState(
                local = LocalSettings(customServicesEnabled = true, barsMarksEnabled = true),
                sharing = SharingSettingsState.Content(SharingSettings()),
                notificationsGranted = false,
                hasCustomSpoiler = true,
                imageBusy = false,
                backgroundWorkRestricted = true,
                calendar = CalendarSyncState(enabled = true)
            ),
            SettingsPageState(
                local = LocalSettings(),
                sharing = SharingSettingsState.Error,
                notificationsGranted = null,
                hasCustomSpoiler = false,
                imageBusy = true,
                backgroundWorkRestricted = false
            )
        )
        SettingsPage.entries.forEach { page ->
            val provider = pages.forPage(page)
            states.forEach { state ->
                val ids = provider.sections(page, state).flatMap(SettingSection::items).map { it.id }
                val foreign = ids.filter { it !in provider.rows && it !in navigationRows && it !in sharedRows }
                assertTrue(foreign.isEmpty(), "$page shows rows of other providers: $foreign")
            }
        }
    }
}
