package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.client.ClientVersion
import kotlin.experimental.ExperimentalNativeApi
import platform.Foundation.NSBundle

/**
 * This build as Backend sees it in `X-App-Version`: `<CFBundleShortVersionString> (<CFBundleVersion>); ios;
 * <distribution>`, where a debug binary (Xcode's Debug configuration, the simulator) is `dev` and every other build
 * (TestFlight, the App Store) is `appstore`.
 */
object IosClientVersion {

    @OptIn(ExperimentalNativeApi::class)
    fun fromMainBundle(): ClientVersion = from(
        shortVersion = NSBundle.mainBundle.objectForInfoDictionaryKey(SHORT_VERSION_KEY),
        bundleVersion = NSBundle.mainBundle.objectForInfoDictionaryKey(BUNDLE_VERSION_KEY),
        debug = Platform.isDebugBinary
    )

    /** A bundle without `CFBundleVersion` reads as [AppBundleVersion.MISSING], as the marketing version does. */
    fun from(shortVersion: Any?, bundleVersion: Any?, debug: Boolean): ClientVersion = ClientVersion(
        versionName = AppBundleVersion.from(shortVersion),
        build = AppBundleVersion.from(bundleVersion),
        platform = "ios",
        distribution = if (debug) "dev" else "appstore"
    )

    private const val SHORT_VERSION_KEY = "CFBundleShortVersionString"
    private const val BUNDLE_VERSION_KEY = "CFBundleVersion"
}
