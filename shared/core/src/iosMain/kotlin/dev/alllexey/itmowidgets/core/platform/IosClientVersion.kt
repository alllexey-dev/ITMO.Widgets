package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.client.ClientVersion
import kotlin.experimental.ExperimentalNativeApi
import platform.Foundation.NSBundle

/**
 * This build as Backend sees it in `X-App-Version`: `<CFBundleShortVersionString> (<CFBundleVersion>); ios;
 * <distribution>`, the distribution as [distribution] tells it from the bundle.
 */
object IosClientVersion {

    @OptIn(ExperimentalNativeApi::class)
    fun fromMainBundle(): ClientVersion {
        val bundle = NSBundle.mainBundle
        // The only synchronous TestFlight signal: StoreKit's AppTransaction is async and Swift-only.
        @Suppress("DEPRECATION")
        val sandboxReceipt = bundle.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt"
        return from(
            shortVersion = bundle.objectForInfoDictionaryKey(SHORT_VERSION_KEY),
            bundleVersion = bundle.objectForInfoDictionaryKey(BUNDLE_VERSION_KEY),
            distribution = distribution(
                debug = Platform.isDebugBinary,
                provisioningProfile = bundle.pathForResource("embedded", ofType = "mobileprovision") != null,
                sandboxReceipt = sandboxReceipt
            )
        )
    }

    /** A bundle without `CFBundleVersion` reads as [AppBundleVersion.MISSING], as the marketing version does. */
    fun from(shortVersion: Any?, bundleVersion: Any?, distribution: String): ClientVersion = ClientVersion(
        versionName = AppBundleVersion.from(shortVersion),
        build = AppBundleVersion.from(bundleVersion),
        platform = "ios",
        distribution = distribution
    )

    /**
     * `dev` for a debug binary (Xcode's Debug configuration, the simulator); `sideload` for a build that carries its
     * provisioning profile (installed from Xcode, with a free Apple ID, through AltStore); `testflight` for a build
     * with a sandbox receipt; `appstore` otherwise. The App Store and TestFlight strip the profile.
     */
    fun distribution(debug: Boolean, provisioningProfile: Boolean, sandboxReceipt: Boolean): String = when {
        debug -> "dev"
        provisioningProfile -> "sideload"
        sandboxReceipt -> "testflight"
        else -> "appstore"
    }

    private const val SHORT_VERSION_KEY = "CFBundleShortVersionString"
    private const val BUNDLE_VERSION_KEY = "CFBundleVersion"
}
