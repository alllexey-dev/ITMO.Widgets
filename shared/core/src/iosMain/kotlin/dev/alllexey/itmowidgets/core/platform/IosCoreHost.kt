package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.core.storage.WidgetReloader
import platform.UIKit.UIViewController

/**
 * Removes every WebKit website record of the app (cookies, storage, caches), as Android's `WebSessionDataCleaner`
 * clears its WebView data. Swift implements it over `WKWebsiteDataStore.default()` and calls [completion] once done.
 */
fun interface WebsiteDataClearer {
    fun clearWebsiteData(completion: () -> Unit)
}

/**
 * What the core graph needs from the Swift app, passed to `iosCoreModule`; IO-05's `IosPlatform` implements it.
 * Called on the main thread.
 */
interface IosCoreHost : WidgetReloader, WebsiteDataClearer {

    /** The controller a system sheet is presented from: the top of the key window, or null without a window. */
    fun topViewController(): UIViewController?
}
