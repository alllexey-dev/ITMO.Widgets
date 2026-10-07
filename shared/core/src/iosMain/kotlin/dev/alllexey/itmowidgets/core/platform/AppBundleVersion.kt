package dev.alllexey.itmowidgets.core.platform

import platform.Foundation.NSBundle

/** The marketing version of this process (`CFBundleShortVersionString`), what Android's `versionName` is. */
object AppBundleVersion {

    fun fromMainBundle(): String = from(NSBundle.mainBundle.objectForInfoDictionaryKey(KEY))

    /**
     * [value] as Xcode expanded it. A bundle without the key (a Kotlin test binary has no Info.plist) reads as
     * [MISSING]; a value Xcode left unexpanded is a build error, never a silent fallback.
     */
    fun from(value: Any?): String {
        val version = (value as? String)?.takeIf { it.isNotBlank() } ?: return MISSING
        require("$(" !in version) { "$KEY in Info.plist is not expanded: $version" }
        return version
    }

    const val MISSING = "0"

    private const val KEY = "CFBundleShortVersionString"
}
