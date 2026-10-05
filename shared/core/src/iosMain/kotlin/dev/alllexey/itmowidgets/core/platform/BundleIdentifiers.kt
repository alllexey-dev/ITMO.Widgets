package dev.alllexey.itmowidgets.core.platform

import platform.Foundation.NSBundle

/**
 * The shared-storage IDs of this process, from its own Info.plist: `AppGroupID` and `KeychainGroup`, which Xcode
 * expands from `Base.xcconfig` (docs/ios.md, Identifiers). An extension's `Bundle.main` is the extension, so each
 * process reads its own; no ID is ever a literal in code.
 *
 * AltStore re-signs a sideloaded build into renamed groups and lists them under `ALTAppGroups` (SP-23); they are
 * tried after the built group.
 */
class BundleIdentifiers(
    val appGroupId: String?,
    val keychainGroup: String?,
    val altStoreAppGroups: List<String> = emptyList()
) {

    /** App Group IDs in the order a container is looked up: the built one first. */
    val appGroupCandidates: List<String>
        get() = (listOfNotNull(appGroupId) + altStoreAppGroups).distinct()

    companion object {

        fun fromMainBundle(): BundleIdentifiers = from(NSBundle.mainBundle)

        fun from(bundle: NSBundle): BundleIdentifiers = BundleIdentifiers(
            appGroupId = bundle.setting(APP_GROUP_KEY),
            keychainGroup = bundle.setting(KEYCHAIN_GROUP_KEY),
            altStoreAppGroups = (bundle.objectForInfoDictionaryKey(ALTSTORE_GROUPS_KEY) as? List<*>)
                .orEmpty()
                .filterIsInstance<String>()
                .filter { it.isNotBlank() }
        )

        /** A value Xcode left unexpanded (`$(...)`) or empty counts as missing. */
        private fun NSBundle.setting(key: String): String? =
            (objectForInfoDictionaryKey(key) as? String)?.takeIf { it.isNotBlank() && "$(" !in it }

        private const val APP_GROUP_KEY = "AppGroupID"
        private const val KEYCHAIN_GROUP_KEY = "KeychainGroup"
        private const val ALTSTORE_GROUPS_KEY = "ALTAppGroups"
    }
}
