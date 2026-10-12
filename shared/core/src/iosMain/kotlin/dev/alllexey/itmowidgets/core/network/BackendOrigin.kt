package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmowidgets.core.url.HttpsNavigationPolicy
import platform.Foundation.NSBundle

/**
 * The Backend origin of this build, `BackendBaseURL` in the app's Info.plist, which Xcode expands from
 * `BACKEND_BASE_URL` in `Base.xcconfig`: currently dev in every configuration.
 */
object BackendOrigin {

    fun fromMainBundle(): String = from(NSBundle.mainBundle.objectForInfoDictionaryKey(INFO_KEY))

    /** [value] when it is an https origin; anything else is a build error, never a silent fallback. */
    fun from(value: Any?): String {
        val origin = value as? String
        require(origin != null && "$(" !in origin && HttpsNavigationPolicy.isNavigable(origin)) {
            "No https $INFO_KEY in Info.plist"
        }
        return origin
    }

    private const val INFO_KEY = "BackendBaseURL"
}
