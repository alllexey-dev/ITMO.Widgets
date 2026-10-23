package dev.alllexey.itmowidgets.ios

import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebHost
import dev.alllexey.itmowidgets.feature.connections.data.PairingHost

/**
 * What the Kotlin graph needs from the Swift app (UIKit, WidgetKit, WebKit), implemented in Swift and passed to
 * `startKoinIos` once per process: the core graph's [IosCoreHost] (widget reloads, the top view controller, WebKit
 * clearing), the BARS session's [BarsWebHost] (WebKit's cookies, the hidden BARS `WKWebView`), pairing's [PairingHost]
 * (CryptoKit's HPKE, LocalAuthentication) and the catalog text ([resolveText]). Called on the main thread, except
 * [resolveText].
 */
interface IosPlatform : IosCoreHost, BarsWebHost, PairingHost {

    /**
     * [text] from the bundle's string tables, as Swift's `UiText.resolved` gives it: the graph's `UiTextResolver`
     * (ADR 0035). Called on any thread.
     */
    fun resolveText(text: UiText): String
}
