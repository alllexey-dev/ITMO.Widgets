package dev.alllexey.itmowidgets.feature.settings.reference

import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.time.Duration
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
 * Puts [create]'s view model into the Fragment's store once it is created, before its view asks `by viewModels()`
 * for one, so a Hilt Fragment renders fixture state without a Hilt binding for it.
 */
internal inline fun <F : Fragment, reified VM : ViewModel> F.withViewModel(crossinline create: () -> VM): F = apply {
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            ViewModelProvider(this@apply, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
            })[VM::class.java]
        }
    })
}

private val STEP: Duration = Duration.ofMillis(20)
