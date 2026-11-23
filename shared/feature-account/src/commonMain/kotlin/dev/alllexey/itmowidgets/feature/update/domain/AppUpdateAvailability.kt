package dev.alllexey.itmowidgets.feature.update.domain

/**
 * Whether the place this build is updated from can deliver an update right now.
 *
 * Backend names one latest version per platform, but a store gets it later (review,
 * a staged rollout) or never (a release of another channel). A build that updates
 * through such a store asks it before the offer opens, so the offer never leads to
 * a page without an update.
 */
fun interface AppUpdateAvailability {

    /**
     * False also when the store could not be asked: like a failed version check,
     * that offers nothing. Implementations do not throw.
     */
    suspend fun isAvailable(update: AppUpdate): Boolean

    companion object {
        /** Nothing to ask: the release page (GitHub) and the App Store page are always there. */
        val Always = AppUpdateAvailability { true }
    }
}
