package dev.alllexey.itmowidgets.app.shell

import android.content.Context
import dev.alllexey.itmowidgets.BuildConfig
import java.io.File

/**
 * Which shell `MainActivity` runs: the Fragment shell ([LEGACY]) or the Navigation 3 shell ([NAV3], `ShellHost`).
 * Release builds always run [DEFAULT]. Debug builds read [overrideFile] first, so the instrumented tests run each body
 * in both shells (`ShellModeRule`) and a developer can try the other one; the file holds a mode's name and nothing
 * else, and an unknown or empty file means [DEFAULT]. No DataStore key, no runtime toggle in release builds.
 */
enum class ShellMode {
    LEGACY,
    NAV3,
    ;

    companion object {
        /** SH-1c flips this line; until then the Navigation 3 shell runs only through the debug override. */
        val DEFAULT: ShellMode = LEGACY

        /** The mode for an activity created now; read once per `onCreate`. */
        fun current(context: Context): ShellMode = if (BuildConfig.DEBUG) debugOverride(context) ?: DEFAULT else DEFAULT

        /** `noBackupFilesDir/debug/shell_mode`: read by debug builds only, never backed up. */
        internal fun overrideFile(context: Context): File = File(context.noBackupFilesDir, "debug/shell_mode")

        private fun debugOverride(context: Context): ShellMode? {
            val file = overrideFile(context)
            if (!file.isFile) return null
            val name = file.readText().trim()
            return entries.firstOrNull { it.name == name }
        }
    }
}
