package dev.alllexey.itmowidgets.feature.debug.ui

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshotTest
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The debug tools' previews in `app/screenshots/` (debug tools stay in `:app`). */
@Config(application = PreviewHostApplication::class)
class DebugToolsScreenshotTest : PreviewScreenshotTest()

/**
 * The application of `:app`'s preview captures. The previews need no Hilt graph, and the real application stops at
 * WorkManager under Robolectric. The compose rule launches `ComponentActivity`, which a library module gets from
 * `ui-test-manifest`'s merged manifest; an application's unit tests do not merge it, so it is registered here.
 */
class PreviewHostApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        shadowOf(packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = ComponentActivity::class.java.name
                packageName = this@PreviewHostApplication.packageName
            },
        )
    }
}
