package dev.alllexey.itmowidgets.core.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** On a phone in another language the app still counts and speaks in Russian (run with the system in en-US). */
@RunWith(AndroidJUnit4::class)
class RussianLocaleTest {
    private val application = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun systemIsNotRussian() {
        assumeFalse("The system locale must not be Russian, or the test proves nothing", systemLanguage() == AppLocale.TAG)
    }

    @Test
    fun activityUsesRussianPluralsAndLibraryStrings() {
        ActivityScenario.launch(SettingsPreviewActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertRussianPlurals(activity)
                assertEquals(UP_DESCRIPTION, activity.getString(androidx.appcompat.R.string.abc_action_bar_up_description))
                logDefaults("activity")
            }
        }
    }

    @Test
    fun applicationContextWithAppLocaleUsesRussianPlurals() {
        logDefaults("application before an activity")
        assertRussianPlurals(application.withAppLocale())
        assertEquals(UP_DESCRIPTION, application.withAppLocale().getString(androidx.appcompat.R.string.abc_action_bar_up_description))
    }

    @Test
    fun applicationContextWithAppLocaleStaysRussianAfterTheLastActivityCloses() {
        // On API 33+ the process configuration drops the per-app locale when its last activity is destroyed.
        ActivityScenario.launch(SettingsPreviewActivity::class.java).close()
        logDefaults("application after an activity")
        assertRussianPlurals(application.withAppLocale())
    }

    // On API 33+ the process configuration, Resources.getSystem() included, may carry the app locale.
    private fun systemLanguage(): String = if (Build.VERSION.SDK_INT >= 33) {
        application.getSystemService(LocaleManager::class.java).systemLocales[0].language
    } else {
        Resources.getSystem().configuration.locales[0].language
    }

    private fun assertRussianPlurals(context: Context) {
        val resources = context.resources
        for ((count, expected) in listOf(1 to "1 пара", 2 to "2 пары", 5 to "5 пар", 21 to "21 пара")) {
            assertEquals(expected, resources.getQuantityString(R.plurals.schedule_lesson_count, count, count))
            assertEquals(
                "Расписание изменилось: $expected",
                resources.getQuantityString(R.plurals.schedule_changes_notification_title, count, count)
            )
        }
        for ((count, expected) in listOf(1 to "Ещё 1 балл", 2 to "Ещё 2 балла", 5 to "Ещё 5 баллов", 21 to "Ещё 21 балл")) {
            assertEquals(expected, resources.getQuantityString(R.plurals.sport_score_remaining_status, count, count))
        }
    }

    /** The process-wide defaults after the override, recorded for the shared-module resolvers (KM-07, iOS). */
    private fun logDefaults(where: String) {
        Log.i(
            TAG,
            "API ${Build.VERSION.SDK_INT} $where: Locale.getDefault()=${Locale.getDefault().toLanguageTag()} " +
                "LocaleList.getDefault()=${LocaleList.getDefault().toLanguageTags()} " +
                "application=${application.resources.configuration.locales.toLanguageTags()}"
        )
    }

    private companion object {
        const val TAG = "RussianLocaleTest"
        const val UP_DESCRIPTION = "Перейти вверх"
    }
}
