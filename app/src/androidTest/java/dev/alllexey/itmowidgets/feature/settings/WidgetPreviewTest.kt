package dev.alllexey.itmowidgets.feature.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.DefaultWidgetPreviewFactory
import dev.alllexey.itmowidgets.core.qr.CustomSpoilerManager
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPreview
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrPreviewBitmapCache
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrBitmapRenderer
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrColorResolver
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsRenderer
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toSettingsPreview
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import dev.alllexey.itmowidgets.testing.ViewChecks.descendants
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import dev.alllexey.itmowidgets.core.diagnostics.NoDiagnostics

@RunWith(AndroidJUnit4::class)
class WidgetPreviewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun warmPreviewBindsSynchronouslyAndSpoilerRevisionInvalidatesCachedImages() = withScreen(SettingsPage.QR_WIDGET) { screen ->
        screen.scenario.onActivity { activity ->
            val settings = checkNotNull(screen.vm.previewSettings.value)
            val another = screen.factory.create(activity, activity.lifecycleScope, settings)
            assertNotNull("Warm preview must have an image before yielding the main thread", another.view.findViewById<ImageView>(R.id.qr_code_image).drawable)
            assertSame(screen.qrBitmap(), (another.view.findViewById<ImageView>(R.id.qr_code_image).drawable as BitmapDrawable).bitmap)
            another.close()
        }
        val palette = Color.WHITE to Color.BLACK
        val before = runBlocking { screen.images.load(palette) }
        assertSame(before, screen.images.cached(palette))
        assertTrue(screen.spoilers.deleteCustomSpoiler())
        assertNull(screen.images.cached(palette))
        val after = runBlocking { screen.images.load(palette) }
        assertNotSame(before, after)
    }

    @Test
    fun scheduleSwitchesUpdateActualWidgetViewsWithoutReplacingThePreview() = withScreen(SettingsPage.COMPACT_SCHEDULE_WIDGET) { screen ->
        lateinit var original: View
        screen.scenario.onActivity {
            original = screen.preview.view
            assertEquals(it.getString(R.string.widget_preview_subject_programming), original.findViewById<TextView>(R.id.title).text)
            screen.toggle(SettingsViewModel.KEY_COMPACT_WIDGET_NEXT_LESSON_EARLY)
        }
        settle()
        screen.scenario.onActivity {
            assertSame(original, screen.preview.view)
            assertEquals(it.getString(R.string.widget_preview_subject_math), original.findViewById<TextView>(R.id.title).text)
            assertTrue(original.findViewById<TextView>(R.id.secondary_text).text.contains(it.getString(R.string.widget_preview_teacher)))
            screen.toggle(SettingsViewModel.KEY_COMPACT_WIDGET_HIDE_TEACHER)
        }
        settle()
        screen.scenario.onActivity {
            assertFalse(original.findViewById<TextView>(R.id.secondary_text).text.contains(it.getString(R.string.widget_preview_teacher)))
            assertNull(original.findViewById<ListView>(R.id.lesson_list))
            // Two switches and the text size choice.
            assertEquals(3, screen.vm.sections.value.flatMap { section -> section.items }.size)
        }
    }

    @Test
    fun fullScheduleControlsOnlyChangeTheDayPreviewAndRetainSampleTime() = withScreen(SettingsPage.FULL_SCHEDULE_WIDGET) { screen ->
        val original = screen.preview.view
        var previousCount = 0
        screen.scenario.onActivity {
            previousCount = original.findViewById<ListView>(R.id.lesson_list).adapter.count
            screen.toggle(SettingsViewModel.KEY_FULL_WIDGET_HIDE_PAST)
        }
        settle()
        screen.scenario.onActivity {
            assertTrue(original.findViewById<ListView>(R.id.lesson_list).adapter.count < previousCount)
            original.findViewById<View>(R.id.preview_time).performClick()
        }
        // Main-loop idleness does not include the dialog window's enter animation.
        // Let its bounds settle before injecting a tap, then address the actual list item.
        settle()
        onData(equalTo(context.getString(R.string.widget_preview_time_evening)))
            .inRoot(isDialog())
            .perform(click())
        screen.scenario.onActivity {
            assertEquals("Evening choice must update the actual preview before toggling tomorrow",
                it.getString(R.string.widget_preview_time_evening_short),
                original.findViewById<TextView>(R.id.preview_time).text)
            assertTrue("Evening choice must be retained in preview state",
                checkNotNull(screen.preview.saveState()).getBoolean("evening"))
            screen.toggle(SettingsViewModel.KEY_FULL_WIDGET_SHOW_TOMORROW)
        }
        settle()
        screen.scenario.onActivity {
            val settings = screen.vm.previewSettings.value as dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings.Schedule
            assertTrue("Tomorrow preference must be persisted before preview rendering",
                settings.appearance.full.showTomorrowWhenTodayIsOver)
            val list = original.findViewById<ListView>(R.id.lesson_list)
            val header = list.adapter.getItem(0) as dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleListWidgetItem
            assertTrue("Adapter must contain tomorrow header: $header", header.tomorrow)
            val title = list.findViewById<TextView>(R.id.day_title).text
            assertTrue("Rendered header must match tomorrow adapter item, got: $title",
                title.startsWith(it.getString(R.string.schedule_widget_tomorrow)))
            val saved = checkNotNull(screen.preview.saveState())
            screen.preview.restoreState(Bundle())
            screen.preview.restoreState(saved)
            assertEquals("18:00", original.findViewById<TextView>(R.id.preview_time).text)
        }
        screen.capture("schedule-tomorrow")
    }

    @Test
    fun qrUsesOnlySamplePayloadAndSupportsSpoilerAnimationPaletteAndImageRefresh() = withScreen(SettingsPage.QR_WIDGET) { screen ->
        lateinit var cover: Bitmap
        screen.scenario.onActivity {
            cover = screen.qrBitmap()
            screen.preview.view.findViewById<View>(R.id.qr_code_image).performClick()
        }
        settle()
        screen.scenario.onActivity {
            assertFalse(cover.sameAs(screen.qrBitmap()))
            screen.toggle(SettingsViewModel.KEY_QR_DYNAMIC_COLORS)
            screen.vm.onChoiceChanged(SettingsViewModel.KEY_QR_ANIMATION, QrAnimationType.NONE.name)
        }
        settle()
        screen.scenario.onActivity {
            screen.preview.view.findViewById<View>(R.id.qr_code_image).performClick()
            screen.toggle(SettingsViewModel.KEY_QR_SPOILER)
        }
        settle()
        screen.scenario.onActivity {
            val generator = QrCodeGenerator()
            val expected = QrBitmapRenderer(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)).render(
                generator.toBooleans(generator.generate("WIDGET PREVIEW")),
                420, Color.WHITE, Color.BLACK, 0.05f, 0.06f
            )
            assertTrue("Preview must contain only the sample payload", expected.sameAs(screen.qrBitmap()))
            expected.recycle()
            assertFalse(screen.preview.view.findViewById<View>(R.id.qr_code_image).isClickable)
            assertEquals(it.getString(R.string.widget_preview_qr_plain_hint), screen.preview.view.findViewById<TextView>(R.id.qr_preview_hint).text)
        }
        screen.capture("qr-black-white")
        // Old, non-square custom files are normalized just as in the real widget.
        val file = File(screen.files, "qr_custom_spoiler/custom_spoiler.png").apply { parentFile!!.mkdirs() }
        val custom = Bitmap.createBitmap(100, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        file.outputStream().use { custom.compress(Bitmap.CompressFormat.PNG, 100, it) }
        custom.recycle()
        screen.scenario.onActivity {
            screen.toggle(SettingsViewModel.KEY_QR_SPOILER)
            screen.preview.refresh()
        }
        settle()
        screen.scenario.onActivity {
            assertEquals(420, screen.qrBitmap().width)
            assertEquals(420, screen.qrBitmap().height)
            assertEquals(Color.MAGENTA, screen.qrBitmap().getPixel(210, 210))
            screen.vm.onChoiceChanged(SettingsViewModel.KEY_QR_ANIMATION, QrAnimationType.FADE.name)
        }
        settle()
        screen.scenario.onActivity { screen.preview.stop() }
        assertTrue(file.delete())
        screen.scenario.onActivity { screen.preview.refresh() }
        settle()
        screen.scenario.onActivity { assertFalse(screen.qrBitmap().getPixel(210, 210) == Color.MAGENTA) }
    }

    @Test
    fun qrAnimationFramesKeepTheirBoundsAndReleaseOnClose() = withScreen(SettingsPage.QR_WIDGET) { screen ->
        for (type in QrAnimationType.entries) {
            screen.scenario.onActivity { screen.vm.onChoiceChanged(SettingsViewModel.KEY_QR_ANIMATION, type.name) }
            settle()
            var width = 0
            var height = 0
            screen.scenario.onActivity {
                val image = screen.preview.view.findViewById<ImageView>(R.id.qr_code_image)
                width = image.width
                height = image.height
                image.performClick()
                screen.captureBitmap("qr-${type.name.lowercase()}-start")
            }
            SystemClock.sleep(130)
            screen.scenario.onActivity {
                screen.captureBitmap("qr-${type.name.lowercase()}-middle")
                val image = screen.preview.view.findViewById<ImageView>(R.id.qr_code_image)
                assertEquals(width, image.width)
                assertEquals(height, image.height)
            }
            settle()
            screen.scenario.onActivity {
                screen.captureBitmap("qr-${type.name.lowercase()}-end")
                screen.preview.stop()
            }
        }
        screen.scenario.onActivity {
            screen.preview.view.findViewById<View>(R.id.qr_code_image).performClick()
            screen.preview.close()
            assertEquals(null, screen.preview.view.findViewById<ImageView>(R.id.qr_code_image).drawable)
        }
    }

    @Test
    fun previewsFitLightDarkDynamicAndNarrowLargeTextWhileControlsScroll() {
        for (spec in Appearances.default) {
            for (page in listOf(SettingsPage.QR_WIDGET, SettingsPage.COMPACT_SCHEDULE_WIDGET, SettingsPage.FULL_SCHEDULE_WIDGET)) {
                withScreen(page, spec) { screen ->
                    screen.scenario.onActivity { activity ->
                        val root = screen.preview.view
                        val scroll = activity.findViewById<ScrollView>(R.id.settings_scroll)
                        assertTrue("Controls must retain usable height", scroll.height >= 160 * activity.resources.displayMetrics.density)
                        if (page != SettingsPage.QR_WIDGET) {
                            val button = root.findViewById<View>(R.id.preview_time)
                            assertTrue(button.height >= 48 * activity.resources.displayMetrics.density)
                        }
                        val before = IntArray(2).also(root::getLocationOnScreen)
                        scroll.fullScroll(View.FOCUS_DOWN)
                        assertArrayEquals(before, IntArray(2).also(root::getLocationOnScreen))
                        scroll.scrollTo(0, 0)
                    }
                    settle()
                    screen.scenario.onActivity { it.findViewById<ScrollView>(R.id.settings_scroll).scrollTo(0, 0) }
                    screen.capture("${page.name.lowercase()}-${spec.name}")
                }
            }
        }
    }

    private fun withScreen(
        page: SettingsPage,
        spec: Appearances.Spec = Appearances.light,
        block: (Screen) -> Unit
    ) {
        SettingsPreviewActivity.appearance = spec.toSettingsPreview()
        val intent = Intent(context, SettingsPreviewActivity::class.java)
            .putExtra(SettingsPreviewActivity.EXTRA_WIDTH_DP, spec.widthDp)
        spec.colorSeed?.let { intent.putExtra(SettingsPreviewActivity.EXTRA_COLOR_SEED, it) }
        ActivityScenario.launch<SettingsPreviewActivity>(intent).use { scenario ->
            val screen = Screen(scenario)
            try {
                scenario.onActivity { screen.attach(it, page) }
                settle()
                block(screen)
            } finally {
                scenario.onActivity { screen.close() }
                screen.files.deleteRecursively()
            }
        }
    }

    private fun settle() = TestUi.settle(650)

    private inner class Screen(val scenario: ActivityScenario<SettingsPreviewActivity>) {
        val files = File(context.cacheDir, "widget-preview-${System.nanoTime()}").apply { mkdirs() }
        lateinit var preview: WidgetPreview
        lateinit var vm: SettingsViewModel
        lateinit var factory: DefaultWidgetPreviewFactory
        lateinit var images: QrPreviewBitmapCache
        lateinit var spoilers: CustomSpoilerManager
        private lateinit var activity: SettingsPreviewActivity

        fun attach(activity: SettingsPreviewActivity, page: SettingsPage) {
            this.activity = activity
            val repository = PreviewRepository()
            vm = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(
                    repository,
                    object : CustomServicesRepository {
                        override fun observeEnabled() = MutableStateFlow(false)
                        override suspend fun isEnabled() = false
                        override suspend fun setEnabled(enabled: Boolean) = Unit
                    },
                    object : OnboardingRepository {
                        override fun observeCompleted() = MutableStateFlow(true)
                        override suspend fun complete() = Unit
                        override suspend fun reset() = Unit
                    },
                    object : WidgetRefreshRequester { override fun refreshAll() = Unit },
                    AppVersion(activity.getString(R.string.app_version)), NoDiagnostics, SavedStateHandle(mapOf(SettingsPage.ARGUMENT to page.name))
                ) as T
            })[SettingsViewModel::class.java]
            activity.findViewById<TextView>(R.id.settings_title).text = page.title.resolve(activity)
            activity.findViewById<View>(R.id.settings_scroll).visibility = View.VISIBLE
            val renderer = SettingsRenderer(activity.sectionsContainer, vm::onToggleChanged, {}, {}, {})
            vm.sections.onEach(renderer::render).launchIn(activity.lifecycleScope)
            val isolated = object : ContextWrapper(activity) { override fun getFilesDir() = files }
            spoilers = CustomSpoilerManager(isolated)
            images = QrPreviewBitmapCache(QrCodeGenerator(), QrBitmapRenderer(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)), spoilers)
            factory = DefaultWidgetPreviewFactory(
                images,
                QrColorResolver(activity, PreviewQrPreferences),
                SchedulePreviewScenario(ScheduleWidgetSelector())
            )
            vm.previewSettings.filterNotNull().onEach { settings ->
                if (!::preview.isInitialized) {
                    preview = factory.create(activity, activity.lifecycleScope, settings)
                    activity.findViewById<FrameLayout>(R.id.widget_preview_container).apply {
                        addView(preview.view)
                        visibility = View.VISIBLE
                    }
                }
                preview.bind(settings)
            }.launchIn(activity.lifecycleScope)
        }

        fun toggle(key: String) {
            val item = vm.sections.value.flatMap { it.items }.filterIsInstance<SettingItem.Toggle>().first { it.key == key }
            val title = item.title.resolve(activity)
            val text = activity.sectionsContainer.descendants().filterIsInstance<TextView>()
                .first { it.id == R.id.setting_title && it.text == title }
            (text.parent.parent as View).performClick()
        }

        fun close() { if (::preview.isInitialized) preview.close() }

        fun qrBitmap() = (preview.view.findViewById<ImageView>(R.id.qr_code_image).drawable as BitmapDrawable).bitmap

        /** The live preview bitmap itself; it stays owned by the drawable. */
        fun captureBitmap(name: String) = Screenshots.save(SCREENSHOTS, name, recycle = false) { qrBitmap() }

        fun capture(name: String) = Screenshots.capture(SCREENSHOTS, name) { settle() }
    }

    private object PreviewQrPreferences : QrAppearancePreferences {
        override suspend fun useDynamicColors() = true
        override suspend fun isSpoilerEnabled() = true
        override suspend fun spoilerAnimationType() = QrAnimationType.CIRCLE
    }

    private class PreviewRepository : SettingsRepository {
        private val local = MutableStateFlow(LocalSettings())
        override fun observeLocalSettings() = local
        override fun observeSharingSettings() = MutableStateFlow<SharingSettingsState>(SharingSettingsState.Disabled)
        override suspend fun refreshSharingSettings() = Unit
        override fun disableSharingSettings() = Unit
        override suspend fun setScheduleVisibility(visibility: SharingVisibility) = AppResult.Success(Unit)
        override suspend fun setFriendsVisibility(visibility: SharingVisibility) = AppResult.Success(Unit)
        override suspend fun setSportVisibility(visibility: SharingVisibility) = AppResult.Success(Unit)
        override suspend fun setCompactWidgetNextLessonEarlyEnabled(enabled: Boolean) { local.value = local.value.copy(scheduleWidget = local.value.scheduleWidget.copy(compact = local.value.scheduleWidget.compact.copy(showNextLessonEarly = enabled))) }
        override suspend fun setCompactWidgetTeacherHidden(hidden: Boolean) { local.value = local.value.copy(scheduleWidget = local.value.scheduleWidget.copy(compact = local.value.scheduleWidget.compact.copy(hideTeacher = hidden))) }
        override suspend fun setFullWidgetTeacherHidden(hidden: Boolean) { local.value = local.value.copy(scheduleWidget = local.value.scheduleWidget.copy(full = local.value.scheduleWidget.full.copy(hideTeacher = hidden))) }

        override suspend fun setFullWidgetPastLessonsHidden(hidden: Boolean) { local.value = local.value.copy(scheduleWidget = local.value.scheduleWidget.copy(full = local.value.scheduleWidget.full.copy(hidePastLessons = hidden))) }
        override suspend fun setFullWidgetTomorrowEnabled(enabled: Boolean) { local.value = local.value.copy(scheduleWidget = local.value.scheduleWidget.copy(full = local.value.scheduleWidget.full.copy(showTomorrowWhenTodayIsOver = enabled))) }
        override suspend fun setCompactWidgetTextSize(size: WidgetTextSize) { local.value = local.value.copy(scheduleWidget = local.value.scheduleWidget.copy(compact = local.value.scheduleWidget.compact.copy(textSize = size))) }
        override suspend fun setFullWidgetTextSize(size: WidgetTextSize) { local.value = local.value.copy(scheduleWidget = local.value.scheduleWidget.copy(full = local.value.scheduleWidget.full.copy(textSize = size))) }
        override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) { local.value = local.value.copy(qrWidget = local.value.qrWidget.copy(dynamicColors = enabled)) }
        override suspend fun setQrSpoilerEnabled(enabled: Boolean) { local.value = local.value.copy(qrWidget = local.value.qrWidget.copy(spoilerEnabled = enabled)) }
        override suspend fun setQrAnimationType(type: QrAnimationType) { local.value = local.value.copy(qrWidget = local.value.qrWidget.copy(animationType = type)) }
        override suspend fun setTeacherSelectorHidden(hidden: Boolean) = Unit
        override suspend fun setTimeSelectorHidden(hidden: Boolean) = Unit
        override suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) {
            local.value = local.value.copy(showSportAutoSign = enabled)
        }
    }

    private companion object {
        const val SCREENSHOTS = "widget-preview-screenshots"
    }
}
