package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The registry of settings page providers: one builder per page, one handler per row. */
class SettingsPagesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Lazy: the fixture's ViewModel needs the main dispatcher the rule installs.
    private val pages by lazy { createFixture().pages }
    private val navigationRows = SettingsPage.entries.filter { it != SettingsPage.ROOT }.map(SettingRowId::navigation)
    /** Shown by the schedule and recordbook pages, handled by the schedule provider. */
    private val sharedRows = setOf(SettingRowId.BACKGROUND_WORK)

    @Test
    fun `every settings page has exactly one provider`() {
        SettingsPage.entries.forEach { page ->
            assertEquals("providers of $page", 1, pages.providers.count { page in it.pages })
        }
    }

    @Test
    fun `every row has exactly one handler and navigation rows are opened by the screen`() {
        SettingRowId.entries.forEach { id ->
            val handlers = pages.providers.count { id in it.rows }
            if (id in navigationRows) {
                assertEquals("handlers of navigation row $id", 0, handlers)
                assertNull(pages.forRow(id))
            } else {
                assertEquals("handlers of $id", 1, handlers)
            }
        }
    }

    @Test
    fun `a page shows only its provider's rows, navigation rows and shared rows`() {
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
                assertTrue("$page shows rows of other providers: $foreign", foreign.isEmpty())
            }
        }
    }
}
