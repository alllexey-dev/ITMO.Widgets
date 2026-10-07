package dev.alllexey.itmowidgets.core.diagnostics

import kotlin.concurrent.AtomicReference
import kotlin.experimental.ExperimentalNativeApi

/**
 * Records every Kotlin exception nothing caught into [IosAppDiagnostics] before Kotlin/Native ends the process, so
 * the next launch shows it in the error journal. Swift crashes (a force unwrap, a precondition) never pass Kotlin and
 * are not recorded; Xcode's organizer and the device's analytics have them.
 */
object IosCrashHook {

    private val installed = AtomicReference<IosAppDiagnostics?>(null)

    /** Installs the hook once per process; a later call keeps the first journal. Returns whether it installed it. */
    @OptIn(ExperimentalNativeApi::class)
    fun install(diagnostics: IosAppDiagnostics): Boolean {
        if (!installed.compareAndSet(null, diagnostics)) return false
        val previous = setUnhandledExceptionHook { error ->
            diagnostics.recordCrash(error)
        }
        // A hook someone installed before still runs; the runtime then terminates as without hooks.
        if (previous != null) {
            setUnhandledExceptionHook { error ->
                diagnostics.recordCrash(error)
                previous(error)
            }
        }
        return true
    }
}
