package dev.alllexey.itmowidgets.core.navigation

import dev.alllexey.itmowidgets.core.url.StrictUri

/** What a verified `https` link to the site asks the app to open. */
sealed interface AppLink {
    data class Profile(val isu: Int) : AppLink

    data class SportLesson(val lessonId: Long) : AppLink

    /** A predicted lesson, named by the catalog lesson it repeats two weeks later. */
    data class PredictedSportLesson(val prototypeLessonId: Long) : AppLink

    /** A link under an app prefix whose identifier cannot be read. */
    data object Malformed : AppLink
}

/**
 * Parses links the app's verified intent filter receives.
 *
 * Only `https` links on [PROD_HOST] and [DEV_HOST] under [PROFILE_PREFIX] and [SPORT_PREFIX] belong to the app; one
 * trailing slash is allowed, query and fragment are ignored. Anything else under those prefixes is [AppLink.Malformed],
 * so a damaged link still reaches the app and explains itself.
 */
object AppLinks {
    const val PROD_HOST = "widgets.alllexey.dev"
    const val DEV_HOST = "dev.widgets.alllexey.dev"
    const val PROFILE_PREFIX = "/u/"
    const val SPORT_PREFIX = "/sport/"
    const val PREDICTED_SPORT_PREFIX = "/sport/p/"

    private val ISU = Regex("[1-9][0-9]{0,9}")
    private val LESSON_ID = Regex("[1-9][0-9]{0,18}")

    /** Null when the link is not the app's: another scheme, host or path. */
    fun parse(url: String?): AppLink? {
        val uri = url?.let(StrictUri::parse) ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host?.lowercase() !in setOf(PROD_HOST, DEV_HOST)) return null
        val path = uri.rawPath.orEmpty()
        return when {
            path.startsWith(PROFILE_PREFIX) -> identifier(path, PROFILE_PREFIX, ISU)
                ?.toIntOrNull()?.let(AppLink::Profile) ?: AppLink.Malformed
            path.startsWith(PREDICTED_SPORT_PREFIX) -> identifier(path, PREDICTED_SPORT_PREFIX, LESSON_ID)
                ?.toLongOrNull()?.let(AppLink::PredictedSportLesson) ?: AppLink.Malformed
            path.startsWith(SPORT_PREFIX) -> identifier(path, SPORT_PREFIX, LESSON_ID)
                ?.toLongOrNull()?.let(AppLink::SportLesson) ?: AppLink.Malformed
            else -> null
        }
    }

    private fun identifier(path: String, prefix: String, format: Regex): String? =
        path.removePrefix(prefix).removeSuffix("/").takeIf(format::matches)
}
