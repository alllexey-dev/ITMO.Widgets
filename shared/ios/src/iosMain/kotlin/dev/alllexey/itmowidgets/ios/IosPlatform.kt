package dev.alllexey.itmowidgets.ios

import dev.alllexey.itmowidgets.core.platform.IosCoreHost

/**
 * What the Kotlin graph needs from the Swift app (UIKit, WidgetKit, WebKit), implemented in Swift and passed to
 * `startKoinIos` once per process: the core graph's [IosCoreHost] (widget reloads, the top view controller, WebKit
 * clearing) plus the widget kinds placed on the home screen. Called on the main thread.
 */
interface IosPlatform : IosCoreHost {

    /**
     * Hands [completion] the kinds of this app's widgets the user has placed (WidgetKit's current configurations),
     * empty when WidgetKit cannot tell; calls it once, on the main thread.
     */
    fun installedWidgetKinds(completion: (Set<String>) -> Unit)
}
