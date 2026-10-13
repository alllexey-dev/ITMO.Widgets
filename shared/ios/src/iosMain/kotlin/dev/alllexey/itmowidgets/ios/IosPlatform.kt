package dev.alllexey.itmowidgets.ios

import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebHost
import dev.alllexey.itmowidgets.feature.weblogin.data.PairingHost

/**
 * What the Kotlin graph needs from the Swift app (UIKit, WidgetKit, WebKit), implemented in Swift and passed to
 * `startKoinIos` once per process: the core graph's [IosCoreHost] (widget reloads, the top view controller, WebKit
 * clearing), the BARS session's [BarsWebHost] (WebKit's cookies, the hidden BARS `WKWebView`), pairing's [PairingHost]
 * (CryptoKit's HPKE, LocalAuthentication). Called on the main thread.
 */
interface IosPlatform : IosCoreHost, BarsWebHost, PairingHost
