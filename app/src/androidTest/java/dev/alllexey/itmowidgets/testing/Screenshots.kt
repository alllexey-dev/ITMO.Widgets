package dev.alllexey.itmowidgets.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Write-only screenshot artefacts. Nothing is captured unless the instrumentation argument
 * `captureScreenshots=true` is passed:
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.captureScreenshots=true
 * ```
 *
 * When enabled, PNGs land in `externalCacheDir/<dirName>/<fileName>.png` (or the external files
 * directory for [Location.FILES]).
 */
object Screenshots {
    const val ARGUMENT = "captureScreenshots"

    enum class Location { CACHE, FILES }

    val enabled: Boolean
        get() = InstrumentationRegistry.getArguments().getString(ARGUMENT) == "true"

    /**
     * Takes a device screenshot through UiAutomation. [prepare] runs first and only when
     * capturing is enabled; put the class's pre-capture settling there.
     */
    fun capture(dirName: String, fileName: String, location: Location = Location.CACHE, prepare: () -> Unit = {}) {
        if (!enabled) return
        prepare()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        write(dirName, fileName, location, bitmap)
    }

    /** Draws [view] synchronously on the calling thread; call from the main thread. */
    fun draw(dirName: String, fileName: String, view: View) {
        if (!enabled) return
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        write(dirName, fileName, Location.CACHE, bitmap)
    }

    /** Draws the view returned by [view] on the main thread. */
    fun drawOnMain(dirName: String, fileName: String, view: () -> View) {
        if (!enabled) return
        InstrumentationRegistry.getInstrumentation().runOnMainSync { draw(dirName, fileName, view()) }
    }

    /** Saves the bitmap produced by [bitmap]; the producer runs only when capturing is enabled. */
    fun save(dirName: String, fileName: String, recycle: Boolean = true, bitmap: () -> Bitmap) {
        if (!enabled) return
        write(dirName, fileName, Location.CACHE, bitmap(), recycle)
    }

    private fun write(dirName: String, fileName: String, location: Location, bitmap: Bitmap, recycle: Boolean = true) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = when (location) {
            Location.CACHE -> context.externalCacheDir
            Location.FILES -> context.getExternalFilesDir(null)
        }
        val directory = File(root, dirName).apply { mkdirs() }
        val file = File(directory, "$fileName.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // UTP uninstalls the target APK after a run, so export before its private/external cache is removed.
        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { output ->
            val exported = File(output, dirName).apply { mkdirs() }
            file.copyTo(File(exported, "$fileName.png"), overwrite = true)
        }
        if (recycle) bitmap.recycle()
    }
}
