package dev.alllexey.itmowidgets.feature.settings

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Parcelable
import android.os.SystemClock
import android.util.SparseArray
import android.view.View
import android.widget.TextView
import android.widget.ScrollView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import dev.alllexey.itmowidgets.feature.settings.presentation.ChoiceOption
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsRenderer
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import dev.alllexey.itmowidgets.core.diagnostics.NoDiagnostics

@RunWith(AndroidJUnit4::class)
class SettingsRendererTest {

    @Test
    fun valueTitleAndFooterChangesPreserveRowsAndUseLatestChoice() = withPreview { activity ->
        val callbacks = mutableListOf<SettingItem.Choice>()
        val renderer = renderer(activity, onChoice = callbacks::add)
        val original = choice()
        val section = SettingSection(text("Раздел"), listOf(toggle(), original), text("Подсказка"))
        renderer.render(listOf(section))
        val switch = activity.sectionsContainer.findViewById<MaterialSwitch>(R.id.setting_switch)
        val choiceRow = rowWithTitle(activity, "Анимация")
        val updated = original.copy(value = text("Круг"), selectedOptionKey = "circle")

        renderer.render(
            listOf(section.copy(title = text("Новый раздел"), items = listOf(toggle(true), updated), footer = null))
        )
        renderer.render(
            listOf(section.copy(title = text("Новый раздел"), items = listOf(toggle(true), updated), footer = text("Новая подсказка")))
        )

        assertSame(switch, activity.sectionsContainer.findViewById<MaterialSwitch>(R.id.setting_switch))
        assertSame(choiceRow, rowWithTitle(activity, "Анимация"))
        assertTrue(switch.isChecked)
        assertNotNull(activity.sectionsContainer.descendants().filterIsInstance<TextView>().find { it.text == "Новый раздел" })
        assertNotNull(activity.sectionsContainer.descendants().filterIsInstance<TextView>().find { it.text == "Новая подсказка" })
        choiceRow.performClick()
        assertEquals(listOf(updated), callbacks)
    }

    @Test
    fun unknownAndDisabledToggleRowsCannotChangeState() = withPreview { activity ->
        val callbacks = mutableListOf<Pair<String, Boolean>>()
        val renderer = renderer(activity, onToggle = { key, value -> callbacks += key to value })

        for (item in listOf(toggle().copy(stateKnown = false), toggle().copy(enabled = false))) {
            renderer.render(listOf(SettingSection(null, listOf(item))))
            val row = rowWithTitle(activity, "Расписание")
            val switch = row.findViewById<MaterialSwitch>(R.id.setting_switch)
            assertFalse(row.isEnabled)
            assertFalse(switch.isEnabled)
            row.performClick()
            assertFalse(switch.isChecked)
        }

        assertTrue(callbacks.isEmpty())
    }

    @Test
    fun stateRestorationDoesNotEmitUserActionsAndTheWholeRowTogglesOnce() = withPreview { activity ->
        val callbacks = mutableListOf<Pair<String, Boolean>>()
        val renderer = renderer(activity, onToggle = { key, value -> callbacks += key to value })
        val section = SettingSection(null, listOf(toggle(true)))
        renderer.render(listOf(section))
        val switch = activity.sectionsContainer.findViewById<MaterialSwitch>(R.id.setting_switch)
        renderer.render(listOf(section))
        assertTrue(switch.isChecked)
        assertTrue(callbacks.isEmpty())

        val row = rowWithTitle(activity, "Расписание")
        assertTrue(row.isClickable)
        row.performClick()
        assertEquals(listOf("toggle" to false), callbacks)

        renderer.render(listOf(section))
        assertSame(switch, activity.sectionsContainer.findViewById<MaterialSwitch>(R.id.setting_switch))
        assertTrue(switch.isChecked)
        assertEquals(1, callbacks.size)
    }

    @Test
    fun navigationUsesUpdatedPageAndDisabledRowsIgnoreActions() = withPreview { activity ->
        val navigated = mutableListOf<SettingsPage>()
        val choices = mutableListOf<SettingItem.Choice>()
        val actions = mutableListOf<String>()
        val renderer = renderer(activity, onNavigate = navigated::add, onChoice = choices::add, onAction = actions::add)
        val navigation = SettingItem.Navigation("navigation", text("Открыть"), page = SettingsPage.SERVICES)
        renderer.render(listOf(SettingSection(null, listOf(navigation))))
        val originalRow = rowWithTitle(activity, "Открыть")
        renderer.render(listOf(SettingSection(null, listOf(navigation.copy(page = SettingsPage.PRIVACY)))))
        assertSame(originalRow, rowWithTitle(activity, "Открыть"))
        originalRow.performClick()
        assertEquals(listOf(SettingsPage.PRIVACY), navigated)

        renderer.render(
            listOf(
                SettingSection(
                    null,
                    listOf(navigation.copy(enabled = false), choice().copy(enabled = false), SettingItem.Action("action", text("Действие"), enabled = false))
                )
            )
        )
        listOf("Открыть", "Анимация", "Действие").forEach { rowWithTitle(activity, it).performClick() }
        assertEquals(1, navigated.size)
        assertTrue(choices.isEmpty())
        assertTrue(actions.isEmpty())
    }

    @Test
    fun hierarchyRestorationCannotOverwriteRepositorySwitchValues() = withPreview { activity ->
        val renderer = renderer(activity)
        val first = toggle(true)
        val second = toggle().copy(key = "second", title = text("Спорт"))
        renderer.render(listOf(SettingSection(null, listOf(first, second))))
        val hierarchy = SparseArray<Parcelable>()
        activity.sectionsContainer.saveHierarchyState(hierarchy)
        renderer.render(listOf(SettingSection(null, listOf(first.copy(checked = false), second.copy(checked = true)))))
        activity.sectionsContainer.restoreHierarchyState(hierarchy)

        assertFalse(rowWithTitle(activity, "Расписание").findViewById<MaterialSwitch>(R.id.setting_switch).isChecked)
        assertTrue(rowWithTitle(activity, "Спорт").findViewById<MaterialSwitch>(R.id.setting_switch).isChecked)
    }

    @Test
    fun nonzeroScrollRestoresWhenRowsArriveAfterActivityRecreation() {
        val sections = listOf(SettingSection(null, (1..30).map { index ->
            toggle(index % 2 == 0).copy(key = "setting-$index", title = text("Настройка $index"))
        }))
        ActivityScenario.launch<SettingsPreviewActivity>(previewIntent()).use { scenario ->
            scenario.onActivity {
            renderer(it).render(sections)
            it.findViewById<View>(R.id.settings_scroll).visibility = if (sections.isEmpty()) View.GONE else View.VISIBLE
            it.findViewById<View>(R.id.settings_progress).visibility = if (sections.isEmpty()) View.VISIBLE else View.GONE
        }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var previousScroll = 0
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<ScrollView>(R.id.settings_scroll)
                scroll.scrollTo(0, 420)
                previousScroll = scroll.scrollY
                assertTrue(previousScroll > 0)
            }

            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.settings_scroll).visibility)
                renderer(activity).render(sections)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(previousScroll, activity.findViewById<ScrollView>(R.id.settings_scroll).scrollY)
                assertFalse(rowWithTitle(activity, "Настройка 1").findViewById<MaterialSwitch>(R.id.setting_switch).isChecked)
                assertTrue(rowWithTitle(activity, "Настройка 2").findViewById<MaterialSwitch>(R.id.setting_switch).isChecked)
            }
        }
    }

    @Test
    fun longTitleAndValueFitNarrowScreenAtLargeFontScale() {
        ActivityScenario.launch<SettingsPreviewActivity>(previewIntent(fontScale = 1.3f, widthDp = 320)).use { scenario ->
            scenario.onActivity { activity ->
                renderer(activity).render(
                    listOf(SettingSection(null, listOf(choice().copy(title = text(LONG_TITLE), value = text(LONG_VALUE)), toggle().copy(title = text(LONG_TOGGLE)))))
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(1.3f, activity.resources.configuration.fontScale, 0.01f)
                val row = rowWithTitle(activity, LONG_TITLE)
                val title = row.findViewById<TextView>(R.id.setting_title)
                val value = row.findViewById<TextView>(R.id.setting_value)
                listOf(title, value, rowWithTitle(activity, LONG_TOGGLE).findViewById<TextView>(R.id.setting_title)).forEach(::assertTextFits)
                val titleBounds = Rect().also(title::getGlobalVisibleRect)
                val valueBounds = Rect().also(value::getGlobalVisibleRect)
                assertFalse("Title and current value must not overlap", Rect.intersects(titleBounds, valueBounds))
                assertTrue("Settings row must retain a 48 dp touch target", row.height >= 48 * activity.resources.displayMetrics.density)
            }
            saveScreenshot(scenario, "settings-narrow-font130")
        }
    }

    @Test
    fun displayedVersionMatchesBuildMetadataAndFitsNarrowScreen() {
        for ((name, dark) in listOf("light" to false, "dark" to true)) {
            ActivityScenario.launch<SettingsPreviewActivity>(
                previewIntent(fontScale = 1.3f, widthDp = 320, dark = dark, colorSeed = 0xFF087F5B.toInt())
            ).use { scenario ->
                renderProductionPage(scenario, SettingsPage.MAINTENANCE)
                scenario.onActivity { activity ->
                    assertEquals(BuildConfig.VERSION_NAME, activity.getString(R.string.app_version))
                    val row = rowWithTitle(activity, activity.getString(R.string.settings_version_title))
                    val value = row.findViewById<TextView>(R.id.setting_value)
                    assertEquals(BuildConfig.VERSION_NAME, value.text.toString())
                    assertTextFits(value)
                    assertTextFits(row.findViewById(R.id.setting_title))
                }
                saveScreenshot(scenario, "settings-version-$name-narrow-font130")
            }
        }
    }

    @Test
    fun captureRootAndDetailInLightDarkAndCustomDynamicPalette() {
        for (spec in Appearances.default) {
            ActivityScenario.launch<SettingsPreviewActivity>(previewIntent(dark = spec.dark, colorSeed = spec.colorSeed)).use { scenario ->
                scenario.onActivity { activity ->
                    val expected = if (spec.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    assertEquals(expected, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                }
                for (page in listOf(SettingsPage.ROOT, SettingsPage.PRIVACY, SettingsPage.QR_WIDGET, SettingsPage.SCHEDULE)) {
                    renderProductionPage(scenario, page)
                    saveScreenshot(scenario, "settings-${page.name.lowercase()}-${spec.name}")
                }
            }
        }
    }

    @Test
    fun scheduleDisplaySettingFitsNarrowScreenWithCustomServicesDisabled() {
        ActivityScenario.launch<SettingsPreviewActivity>(previewIntent(fontScale = 1.3f, widthDp = 320)).use { scenario ->
            renderProductionPage(scenario, SettingsPage.SCHEDULE, PreviewRepository(SharingSettingsState.Disabled))
            scenario.onActivity { activity ->
                val row = rowWithTitle(activity, activity.getString(R.string.settings_schedule_sport_auto_sign_title))
                assertTrue(row.isEnabled)
                assertTextFits(row.findViewById(R.id.setting_title))
                assertTextFits(row.findViewById(R.id.setting_description))
                assertTrue("Settings row must retain a 48 dp touch target", row.height >= 48 * activity.resources.displayMetrics.density)
            }
            saveScreenshot(scenario, "settings-schedule-disabled-services-narrow-font130")
        }
    }

    @Test
    fun capturePrivacyLoadingErrorAndDisabledStates() {
        for ((name, sharing) in listOf("loading" to SharingSettingsState.Loading, "error" to SharingSettingsState.Error, "disabled" to SharingSettingsState.Disabled)) {
            ActivityScenario.launch<SettingsPreviewActivity>(previewIntent(fontScale = 1.3f, widthDp = 320)).use { scenario ->
                renderProductionPage(scenario, SettingsPage.PRIVACY, PreviewRepository(sharing))
                scenario.onActivity { activity ->
                    if (sharing == SharingSettingsState.Loading) {
                        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_progress).visibility)
                        assertEquals(View.GONE, activity.findViewById<View>(R.id.settings_scroll).visibility)
                    } else {
                        listOf(R.string.settings_schedule_sharing_title, R.string.settings_sport_sharing_title, R.string.settings_friends_sharing_title).forEach { title ->
                            val row = rowWithTitle(activity, activity.getString(title))
                            assertFalse(row.isEnabled)
                            assertEquals(activity.getString(R.string.settings_privacy_unknown), row.findViewById<TextView>(R.id.setting_value).text.toString())
                            assertTextFits(row.findViewById(R.id.setting_title))
                            assertTextFits(row.findViewById(R.id.setting_value))
                        }
                    }
                    assertTrue(activity.sectionsContainer.descendants().none { it is MaterialSwitch })
                }
                saveScreenshot(scenario, "settings-privacy-$name-narrow")
            }
        }
    }

    @Test
    fun privacyAudienceChoicesFitLightDarkAndNarrowDynamicPalettes() {
        // Always narrow and at the large font scale; the matrix only varies the palette.
        for (spec in Appearances.default) {
            ActivityScenario.launch<SettingsPreviewActivity>(
                previewIntent(fontScale = 1.3f, widthDp = 320, dark = spec.dark, colorSeed = spec.colorSeed)
            ).use { scenario ->
                renderProductionPage(scenario, SettingsPage.PRIVACY, PreviewRepository(
                    SharingSettingsState.Content(SharingSettings(SharingVisibility.ALL, SharingVisibility.NOBODY))
                ))
                scenario.onActivity { activity ->
                    val schedule = rowWithTitle(activity, activity.getString(R.string.settings_schedule_sharing_title))
                    val sport = rowWithTitle(activity, activity.getString(R.string.settings_sport_sharing_title))
                    assertEquals(activity.getString(R.string.settings_privacy_all), schedule.findViewById<TextView>(R.id.setting_value).text.toString())
                    assertEquals(activity.getString(R.string.settings_privacy_nobody), sport.findViewById<TextView>(R.id.setting_value).text.toString())
                    val friends = rowWithTitle(activity, activity.getString(R.string.settings_friends_sharing_title))
                    assertEquals(activity.getString(R.string.settings_privacy_all), friends.findViewById<TextView>(R.id.setting_value).text.toString())
                    listOf(schedule, sport, friends).forEach { row ->
                        assertTrue(row.isEnabled)
                        assertTrue(row.isClickable)
                        assertTrue("Audience choice needs a 48 dp target", row.height >= 48 * activity.resources.displayMetrics.density)
                        assertTextFits(row.findViewById(R.id.setting_title))
                        assertTextFits(row.findViewById(R.id.setting_value))
                        val titleBounds = Rect().also(row.findViewById<TextView>(R.id.setting_title)::getGlobalVisibleRect)
                        val valueBounds = Rect().also(row.findViewById<TextView>(R.id.setting_value)::getGlobalVisibleRect)
                        assertFalse(Rect.intersects(titleBounds, valueBounds))
                    }
                    assertTrue(activity.sectionsContainer.descendants().none { it is MaterialSwitch })
                }
                saveScreenshot(scenario, "settings-privacy-audience-${spec.name}-narrow-font130")
            }
        }
    }

    @Test
    fun privacyRowsUseCurrentAudienceAndPreserveIdentityWhenUpdatesLockThem() {
        ActivityScenario.launch<SettingsPreviewActivity>(previewIntent()).use { scenario ->
            val repository = PreviewRepository(SharingSettingsState.Content(SharingSettings()))
            val viewModel = renderProductionPage(scenario, SettingsPage.PRIVACY, repository)
            scenario.onActivity { activity ->
                val choices = mutableListOf<SettingItem.Choice>()
                val renderer = renderer(activity, onChoice = choices::add)
                renderer.render(viewModel.sections.value)
                val schedule = rowWithTitle(activity, activity.getString(R.string.settings_schedule_sharing_title))
                val sport = rowWithTitle(activity, activity.getString(R.string.settings_sport_sharing_title))
                assertEquals(activity.getString(R.string.settings_privacy_friends), schedule.findViewById<TextView>(R.id.setting_value).text.toString())
                assertEquals(activity.getString(R.string.settings_privacy_friends), sport.findViewById<TextView>(R.id.setting_value).text.toString())
                schedule.performClick()
                assertEquals(1, choices.size)
                assertEquals(listOf("ALL", "FRIENDS", "NOBODY"), choices.single().options.map { it.key })
                assertEquals(listOf("Все", "Друзья", "Никто"), choices.single().options.map { it.label.resolve(activity) })
                assertEquals(SharingVisibility.FRIENDS.name, choices.single().selectedOptionKey)
                val locked = viewModel.sections.value.map { section ->
                    section.copy(items = section.items.map { item ->
                        if (item is SettingItem.Choice) item.copy(enabled = false) else item
                    })
                }
                renderer.render(locked)
                assertSame(schedule, rowWithTitle(activity, activity.getString(R.string.settings_schedule_sharing_title)))
                assertSame(sport, rowWithTitle(activity, activity.getString(R.string.settings_sport_sharing_title)))
                schedule.performClick()
                sport.performClick()
                assertEquals(1, choices.size)
                assertFalse(schedule.isEnabled)
                assertFalse(sport.isEnabled)
                assertEquals(activity.getString(R.string.settings_privacy_friends), schedule.findViewById<TextView>(R.id.setting_value).text.toString())
            }
        }
    }

    private fun renderProductionPage(
        scenario: ActivityScenario<SettingsPreviewActivity>,
        page: SettingsPage,
        repository: PreviewRepository = PreviewRepository()
    ): SettingsViewModel {
        lateinit var viewModel: SettingsViewModel
        scenario.onActivity { activity ->
            viewModel = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
                    repository,
                    object : CustomServicesRepository {
                        override fun observeEnabled() = MutableStateFlow(true)
                        override suspend fun isEnabled() = true
                        override suspend fun setEnabled(enabled: Boolean) = Unit
                    },
                    object : OnboardingRepository {
                        override fun observeCompleted() = MutableStateFlow(true)
                        override suspend fun complete() = Unit
                        override suspend fun reset() = Unit
                    },
                    object : WidgetRefreshRequester {
                        override fun refreshAll() = Unit
                    },
                    AppVersion(activity.getString(R.string.app_version)),
                    NoDiagnostics,
                    SavedStateHandle(mapOf(SettingsPage.ARGUMENT to page.name))
                ) as T
            })[page.name, SettingsViewModel::class.java]
            viewModel.onNotificationPermissionChanged(true)
            activity.findViewById<TextView>(R.id.settings_title).text = page.title.resolve(activity)
        }
        val sections = if (page == SettingsPage.PRIVACY && repository.initialSharing == SharingSettingsState.Loading) {
            emptyList()
        } else runBlocking {
            withTimeout(5_000) {
                viewModel.sections.first { sections ->
                    sections.isNotEmpty() && sections.flatMap(SettingSection::items)
                        .filterIsInstance<SettingItem.Action>()
                        .none { it.value == UiText.Resource(R.string.settings_notifications_checking) }
                }
            }
        }
        scenario.onActivity {
            renderer(it).render(sections)
            it.findViewById<View>(R.id.settings_scroll).visibility = if (sections.isEmpty()) View.GONE else View.VISIBLE
            it.findViewById<View>(R.id.settings_progress).visibility = if (sections.isEmpty()) View.VISIBLE else View.GONE
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        return viewModel
    }

    private fun renderer(
        activity: SettingsPreviewActivity,
        onToggle: (String, Boolean) -> Unit = { _, _ -> },
        onChoice: (SettingItem.Choice) -> Unit = {},
        onNavigate: (SettingsPage) -> Unit = {},
        onAction: (String) -> Unit = {}
    ): SettingsRenderer {
        activity.findViewById<View>(R.id.settings_scroll).visibility = View.VISIBLE
        return SettingsRenderer(activity.sectionsContainer, onToggle, onChoice, onNavigate, onAction)
    }

    private fun withPreview(block: (SettingsPreviewActivity) -> Unit) {
        ActivityScenario.launch<SettingsPreviewActivity>(previewIntent()).use { it.onActivity(block) }
    }

    private fun previewIntent(fontScale: Float = 1f, widthDp: Int = 0, dark: Boolean = false, colorSeed: Int? = null) =
        Intent(ApplicationProvider.getApplicationContext(), SettingsPreviewActivity::class.java).apply {
            SettingsPreviewActivity.appearance = SettingsPreviewActivity.Appearance(fontScale, dark)
            putExtra(SettingsPreviewActivity.EXTRA_WIDTH_DP, widthDp)
            colorSeed?.let { putExtra(SettingsPreviewActivity.EXTRA_COLOR_SEED, it) }
        }

    private fun rowWithTitle(activity: SettingsPreviewActivity, title: String): View =
        activity.sectionsContainer.descendants().filterIsInstance<TextView>()
            .first { it.id == R.id.setting_title && it.text.toString() == title }.parent.parent as View

    private fun assertTextFits(view: TextView) {
        val layout = view.layout
        assertNotNull(layout)
        assertTrue("Text must be fully measured: ${view.text}", layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
        assertEquals(view.text.length, layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) {
            assertEquals("Text must not be ellipsized: ${view.text}", 0, layout.getEllipsisCount(line))
            assertTrue(layout.getLineWidth(line) <= view.width - view.compoundPaddingLeft - view.compoundPaddingRight + 1)
        }
    }

    private fun saveScreenshot(scenario: ActivityScenario<SettingsPreviewActivity>, name: String) =
        Screenshots.capture("settings-screenshots", name) {
            lateinit var activity: SettingsPreviewActivity
            scenario.onActivity { activity = it }
            TestUi.awaitFrameCommit(activity, "Updated settings frame must be submitted")
            // A committed buffer may still be in the compositor behind the activity transition.
            SystemClock.sleep(300)
        }

    private fun toggle(checked: Boolean = false) = SettingItem.Toggle("toggle", text("Расписание"), checked = checked)

    private fun choice() = SettingItem.Choice(
        "choice", text("Анимация"), text("Плавное исчезновение"),
        listOf(ChoiceOption("fade", text("Плавное исчезновение")), ChoiceOption("circle", text("Круг"))), "fade"
    )

    private fun text(value: String) = UiText.Dynamic(value)

    /** Static in-memory inputs exercise the real presentation without credentials or I/O. */
    private class PreviewRepository(
        val initialSharing: SharingSettingsState = SharingSettingsState.Content(SharingSettings(SharingVisibility.FRIENDS, SharingVisibility.NOBODY))
    ) : SettingsRepository {
        private val local = MutableStateFlow(LocalSettings(customServicesEnabled = initialSharing != SharingSettingsState.Disabled))
        private val sharing = MutableStateFlow(initialSharing)
        override fun observeLocalSettings() = local
        override fun observeSharingSettings() = sharing
        override suspend fun refreshSharingSettings() = Unit
        override fun disableSharingSettings() = Unit
        override suspend fun setScheduleVisibility(visibility: SharingVisibility) = AppResult.Success(Unit)
        override suspend fun setFriendsVisibility(visibility: SharingVisibility) = AppResult.Success(Unit)
        override suspend fun setSportVisibility(visibility: SharingVisibility) = AppResult.Success(Unit)
        override suspend fun setCompactWidgetNextLessonEarlyEnabled(enabled: Boolean) = Unit
        override suspend fun setCompactWidgetTeacherHidden(hidden: Boolean) = Unit
        override suspend fun setFullWidgetTeacherHidden(hidden: Boolean) = Unit

        override suspend fun setFullWidgetPastLessonsHidden(hidden: Boolean) = Unit
        override suspend fun setFullWidgetTomorrowEnabled(enabled: Boolean) = Unit
        override suspend fun setCompactWidgetTextSize(size: WidgetTextSize) = Unit
        override suspend fun setFullWidgetTextSize(size: WidgetTextSize) = Unit
        override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) = Unit
        override suspend fun setQrSpoilerEnabled(enabled: Boolean) = Unit
        override suspend fun setQrAnimationType(type: QrAnimationType) = Unit
        override suspend fun setTeacherSelectorHidden(hidden: Boolean) = Unit
        override suspend fun setTimeSelectorHidden(hidden: Boolean) = Unit
        override suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) {
            local.value = local.value.copy(showSportAutoSign = enabled)
        }
    }

    private companion object {
        const val LONG_TITLE = "Анимация скрытия и раскрытия изображения QR-кода"
        const val LONG_VALUE = "Плавное исчезновение пользовательского изображения"
        const val LONG_TOGGLE = "Показывать расписание следующего дня после окончания сегодняшних занятий"
    }
}
