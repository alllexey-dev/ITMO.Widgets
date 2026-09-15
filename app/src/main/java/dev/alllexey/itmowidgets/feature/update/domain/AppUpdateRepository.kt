package dev.alllexey.itmowidgets.feature.update.domain

import java.time.Instant

/** What the user already decided about update offers. */
data class AppUpdateReminder(
    /** Offers up to this version stay silent; defaults to the installed build. */
    val skippedVersion: AppVersionName,
    /** When the last offer was shown. [Instant.EPOCH] means "never". */
    val notifiedAt: Instant
)

interface AppUpdateRepository {

    /**
     * Null when the backend opt-in is off, the check failed, or the installed
     * build is already current. A failed check is silent: the offer is advisory
     * and must not turn into an error on a screen the user did not ask for.
     */
    suspend fun loadUpdate(): AppUpdate?

    suspend fun reminder(): AppUpdateReminder

    suspend fun markNotified()

    suspend fun skip(version: AppVersionName)
}
