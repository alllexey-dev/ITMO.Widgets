package dev.alllexey.itmowidgets.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.WidgetPickerPreviews
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.ui.widget.WidgetColors
import dev.alllexey.itmowidgets.core.ui.widget.inSystemNightMode
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.SchedulePreviewScenario
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The widgets the launcher shows before one is placed: drawn in the system's mode, the picker's given only on a change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PreviewHostApplication::class)
class LauncherWidgetPreviewsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aPreviewInsideADarkAppOnALightSystemDrawsLight() {
        val darkApp = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        })

        val preview = darkApp.inSystemNightMode()

        assertEquals(Configuration.UI_MODE_NIGHT_NO, preview.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(ContextCompat.getColor(context, R.color.widget_surface), WidgetColors(preview, null).resolve(R.color.widget_surface))
        assertSame(context, context.inSystemNightMode())
    }

    @Test
    fun thePickerGetsItsPreviewsOnceUntilAnInputChanges() = runTest {
        val settings = WidgetSettingsPreferences(InMemoryPreferencesDataStore())
        val previews = WidgetPickerPreviews(
            context, SchedulePreviewScenario(ScheduleWidgetSelector()), { null }, settings,
            DeviceHintPreferences(InMemoryPreferencesDataStore()), CoroutineScope(coroutineContext),
        )
        val manager = AppWidgetManager.getInstance(context)
        val next = ComponentName(context, WidgetProviders.SINGLE_LESSON)
        fun shown() = manager.getWidgetPreview(next, Process.myUserHandle(), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)

        previews.publishNow()
        assertNotNull(shown())

        manager.removeWidgetPreview(next, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        previews.publishNow()
        assertNull(shown())

        settings.setCompactWidgetTeacherHidden(true)
        previews.publishNow()
        assertNotNull(shown())
    }
}
