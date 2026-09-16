package dev.alllexey.itmowidgets.core.diagnostics

/** Writes the crash for the next start, then lets the platform handler kill the process. */
class DiagnosticsCrashHandler private constructor(
    private val diagnostics: FileAppDiagnostics,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            diagnostics.recordCrash(thread.name, error)
        } catch (_: Throwable) {
            // A failing journal must never mask the original crash.
        }
        previous?.uncaughtException(thread, error)
    }

    companion object {
        fun install(diagnostics: FileAppDiagnostics) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            if (previous is DiagnosticsCrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(DiagnosticsCrashHandler(diagnostics, previous))
        }
    }
}
