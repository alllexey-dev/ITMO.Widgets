package dev.alllexey.itmowidgets.feature.settings.reference

import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import java.time.Duration
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf

/**
 * Runs the main looper [advanceMs] ahead, then until [ready] holds (10 s at most), polling as `XmlReferenceCapture`
 * does for work that finishes on other threads.
 */
internal fun settle(advanceMs: Long = 0, ready: () -> Boolean = { true }) {
    val looper = shadowOf(Looper.getMainLooper())
    looper.idle()
    if (advanceMs > 0) looper.idleFor(Duration.ofMillis(advanceMs))
    val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
    while (!ready()) {
        check(System.nanoTime() < deadline) { "The reference was not ready within 10 s" }
        looper.idleFor(STEP)
        Thread.sleep(STEP.toMillis())
    }
    looper.idle()
}

/**
 * Defines [create]'s view model in Koin, where the Fragment's `by viewModel()` asks for it, so a Hilt Fragment
 * renders fixture state. The test class stops Koin around each test with `StopKoinRule`.
 */
internal inline fun <F : Fragment, reified VM : ViewModel> F.withViewModel(crossinline create: () -> VM): F = apply {
    val fixture = module { viewModel<VM> { create() } }
    // The first view of a test starts Koin; the next appearances replace the definition with their own.
    runCatching { loadKoinModules(fixture) }.onFailure { startKoin { modules(fixture) } }
}

private val STEP: Duration = Duration.ofMillis(20)
