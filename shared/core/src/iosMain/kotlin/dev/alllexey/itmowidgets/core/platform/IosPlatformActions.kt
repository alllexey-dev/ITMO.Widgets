package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.url.HttpsNavigationPolicy
import dev.alllexey.itmowidgets.core.url.TelegramLinks
import dev.alllexey.itmowidgets.core.url.UrlEncoding
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The iOS [PlatformActions]: the share sheet over [host]'s top controller, links and Apple Maps through
 * `UIApplication.open`, the app's pages in Settings. Callable from any thread: UIKit runs only on the main one, so a
 * call from elsewhere (a suspend function Swift awaits resumes on a background queue) is queued there and answers
 * true, the presenter unknown until then.
 */
class IosPlatformActions(private val host: IosCoreHost) : PlatformActions {

    /** iOS's share sheet has no heading, so [title] is not shown. */
    override fun shareText(title: String, text: String): Boolean = onMain {
        val presenter = host.topViewController() ?: return@onMain false
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        presenter.presentViewController(sheet, animated = true, completion = null)
        true
    }

    /** t.me links go to Telegram when it is installed, otherwise the https page opens. */
    override fun openLink(url: String): Boolean {
        if (!HttpsNavigationPolicy.isNavigable(url)) return false
        val page = NSURL.URLWithString(url) ?: return false
        val telegram = TelegramLinks.deepLink(url)?.let(NSURL::URLWithString)
        if (telegram == null) {
            open(page)
        } else {
            open(telegram) { opened -> if (!opened) open(page) }
        }
        return true
    }

    override fun openMap(destination: MapDestination): Boolean = openUrl(appleMapsUrl(destination))

    override fun openAppSettings(): Boolean = openUrl(UIApplicationOpenSettingsURLString)

    override fun openNotificationSettings(): Boolean = openUrl(UIApplicationOpenNotificationSettingsURLString)

    private fun openUrl(url: String): Boolean {
        open(NSURL.URLWithString(url) ?: return false)
        return true
    }

    private fun open(url: NSURL, completion: (Boolean) -> Unit = {}) {
        onMain {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = completion)
            true
        }
    }

    /** [action]'s answer on the main thread; from another thread [action] is queued there and the answer is true. */
    private fun onMain(action: () -> Boolean): Boolean {
        if (NSThread.isMainThread) return action()
        dispatch_async(dispatch_get_main_queue()) { action() }
        return true
    }

    companion object {

        /** The pin at the coordinates when known, else Apple Maps' search for the address. */
        fun appleMapsUrl(destination: MapDestination): String = with(destination) {
            if (latitude != null && longitude != null) {
                "$APPLE_MAPS?ll=$latitude,$longitude&q=${UrlEncoding.percentEncode(label)}"
            } else {
                "$APPLE_MAPS?q=${UrlEncoding.percentEncode(address)}"
            }
        }

        private const val APPLE_MAPS = "https://maps.apple.com/"
    }
}
