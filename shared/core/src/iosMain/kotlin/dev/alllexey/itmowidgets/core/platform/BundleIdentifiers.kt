package dev.alllexey.itmowidgets.core.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSISOLatin1StringEncoding
import platform.Foundation.NSPropertyListSerialization
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfFile

/**
 * The shared-storage IDs of this process, from its own Info.plist: `AppGroupID` and `KeychainGroup`, which Xcode
 * expands from `Base.xcconfig` (docs/ios.md, Identifiers). An extension's `Bundle.main` is the extension, so each
 * process reads its own; no ID is ever a literal in code.
 *
 * AltStore and SideStore re-sign a sideloaded build into renamed groups (`<group>.<TEAMID>`). AltStore lists them
 * under `ALTAppGroups`; SideStore no longer does (since its 2026-09-15 builds), so the groups the bundle's own
 * `embedded.mobileprovision` grants come last. Both are tried after the built group.
 */
class BundleIdentifiers(
    val appGroupId: String?,
    val keychainGroup: String?,
    val altStoreAppGroups: List<String> = emptyList(),
    val provisionedAppGroups: List<String> = emptyList()
) {

    /** App Group IDs in the order a container is looked up: the built one first. */
    val appGroupCandidates: List<String>
        get() = (listOfNotNull(appGroupId) + altStoreAppGroups + provisionedAppGroups).distinct()

    companion object {

        fun fromMainBundle(): BundleIdentifiers = from(NSBundle.mainBundle)

        fun from(bundle: NSBundle): BundleIdentifiers = BundleIdentifiers(
            appGroupId = bundle.setting(APP_GROUP_KEY),
            keychainGroup = bundle.setting(KEYCHAIN_GROUP_KEY),
            altStoreAppGroups = (bundle.objectForInfoDictionaryKey(ALTSTORE_GROUPS_KEY) as? List<*>)
                .orEmpty()
                .filterIsInstance<String>()
                .filter { it.isNotBlank() },
            provisionedAppGroups = bundle.pathForResource("embedded", "mobileprovision")
                ?.let(::provisionedAppGroups)
                .orEmpty()
        )

        /**
         * The App Groups the provisioning profile at [path] grants. The profile is a CMS envelope around a plain XML
         * plist, cut out through Latin-1, which maps every byte to one character; anything unreadable gives none.
         */
        @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
        internal fun provisionedAppGroups(path: String): List<String> {
            val data = NSData.dataWithContentsOfFile(path) ?: return emptyList()
            val text = NSString.create(data = data, encoding = NSISOLatin1StringEncoding)?.toString()
                ?: return emptyList()
            val start = text.indexOf(PLIST_START).takeIf { it >= 0 } ?: return emptyList()
            val end = text.indexOf(PLIST_END, start).takeIf { it >= 0 } ?: return emptyList()
            @Suppress("CAST_NEVER_SUCCEEDS")
            val plist = (text.substring(start, end + PLIST_END.length) as NSString)
                .dataUsingEncoding(NSISOLatin1StringEncoding)
                ?.let { NSPropertyListSerialization.propertyListWithData(it, 0uL, null, null) } as? Map<*, *>
            val entitlements = plist?.get("Entitlements") as? Map<*, *>
            return (entitlements?.get(APP_GROUPS_ENTITLEMENT) as? List<*>).orEmpty().filterIsInstance<String>()
        }

        /** A value Xcode left unexpanded (`$(...)`) or empty counts as missing. */
        private fun NSBundle.setting(key: String): String? =
            (objectForInfoDictionaryKey(key) as? String)?.takeIf { it.isNotBlank() && "$(" !in it }

        private const val APP_GROUP_KEY = "AppGroupID"
        private const val KEYCHAIN_GROUP_KEY = "KeychainGroup"
        private const val ALTSTORE_GROUPS_KEY = "ALTAppGroups"
        private const val APP_GROUPS_ENTITLEMENT = "com.apple.security.application-groups"
        private const val PLIST_START = "<?xml"
        private const val PLIST_END = "</plist>"
    }
}
