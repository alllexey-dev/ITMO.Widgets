package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.core.location.MapDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIViewController
import kotlin.concurrent.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class IosPlatformActionsTest {

    @Test
    fun pinsTheCoordinatesInAppleMapsWhenKnown() {
        val destination = MapDestination("Main building", "Kronverksky 49", 59.957, 30.308)

        assertEquals(
            "https://maps.apple.com/?ll=59.957,30.308&q=Main%20building",
            IosPlatformActions.appleMapsUrl(destination)
        )
    }

    @Test
    fun searchesTheAddressWithoutCoordinates() {
        assertEquals(
            "https://maps.apple.com/?q=Lomonosova%209",
            IosPlatformActions.appleMapsUrl(MapDestination("Building", "Lomonosova 9"))
        )
    }

    /** A suspend function Swift awaits resumes on a background queue; UIKit must still see the main thread. */
    @Test
    fun sharesFromABackgroundDispatcherOnTheMainThread() {
        val host = RecordingHost()
        val answers = AtomicInt(0)
        CoroutineScope(Dispatchers.Default).launch {
            if (IosPlatformActions(host).shareText("Sport", "Lesson https://example.org")) answers.incrementAndGet()
        }

        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (host.presentations.value == 0 && deadline.hasNotPassedNow()) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
        }

        assertEquals(1, host.presentations.value, "the sheet is presented once, on the main thread")
        assertEquals(0, host.offMainLookups.value)
        assertEquals(1, answers.value)
    }

    private class RecordingHost : IosCoreHost {
        val presentations = AtomicInt(0)
        val offMainLookups = AtomicInt(0)

        override fun topViewController(): UIViewController {
            if (!NSThread.isMainThread) offMainLookups.incrementAndGet()
            return object : UIViewController(nibName = null, bundle = null) {
                override fun presentViewController(
                    viewControllerToPresent: UIViewController,
                    animated: Boolean,
                    completion: (() -> Unit)?
                ) {
                    if (NSThread.isMainThread) presentations.incrementAndGet()
                }
            }
        }

        override fun reload(kind: String) = Unit

        override fun clearWebsiteData(completion: () -> Unit) = completion()
    }
}
