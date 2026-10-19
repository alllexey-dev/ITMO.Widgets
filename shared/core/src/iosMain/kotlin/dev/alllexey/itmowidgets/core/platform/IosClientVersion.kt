package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.client.BuildIdentity
import dev.alllexey.itmowidgets.client.ClientHeader
import dev.alllexey.itmowidgets.client.ClientVersion
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

/**
 * This build as Backend sees it in `X-App-Version`: `<CFBundleShortVersionString> (<CFBundleVersion>); ios;
 * <distribution>`, the distribution as [distribution] tells it from the bundle; and in `X-Client`
 * ([headerFromMainBundle]).
 */
object IosClientVersion {

    fun fromMainBundle(): ClientVersion {
        val bundle = NSBundle.mainBundle
        return from(
            shortVersion = bundle.objectForInfoDictionaryKey(SHORT_VERSION_KEY),
            bundleVersion = bundle.objectForInfoDictionaryKey(BUNDLE_VERSION_KEY),
            distribution = mainBundleDistribution()
        )
    }

    /**
     * `X-Client` (ADR 0034): `kind=ios; version=...; build=...; channel=<distribution>; os=<iOS major>; install=...`.
     * The notification service sends the same from Swift (`PushHandler.clientHeader`).
     */
    fun headerFromMainBundle(installId: suspend () -> String?): ClientHeader {
        val bundle = NSBundle.mainBundle
        return ClientHeader(
            kind = "ios",
            version = AppBundleVersion.from(bundle.objectForInfoDictionaryKey(SHORT_VERSION_KEY)),
            build = AppBundleVersion.from(bundle.objectForInfoDictionaryKey(BUNDLE_VERSION_KEY)),
            // Build identity: plug in the Info.plist keys of ci/simple-builds here (channel `custom` for an
            // unofficial build, else the distribution; source, commit and the upstream base version).
            identity = BuildIdentity(channel = mainBundleDistribution()),
            os = osMajorVersion(),
            installId = installId
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun osMajorVersion(): String =
        NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion.toString() }

    @OptIn(ExperimentalNativeApi::class)
    private fun mainBundleDistribution(): String {
        val bundle = NSBundle.mainBundle
        // The only synchronous TestFlight signal: StoreKit's AppTransaction is async and Swift-only.
        @Suppress("DEPRECATION")
        val sandboxReceipt = bundle.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt"
        return distribution(
            debug = Platform.isDebugBinary,
            provisioningProfile = bundle.pathForResource("embedded", ofType = "mobileprovision") != null,
            sandboxReceipt = sandboxReceipt
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
