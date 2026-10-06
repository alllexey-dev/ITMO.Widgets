package dev.alllexey.itmowidgets.feature.update.domain

/**
 * A release newer than the installed build.
 *
 * @property note Plain text written by the backend; it may be empty.
 * @property unsupported The installed build is older than the minimum version the
 * backend still serves, so postponing it does not help — the offer stays.
 */
data class AppUpdate(
    val installed: AppVersionName,
    val latest: AppVersionName,
    val note: String,
    val unsupported: Boolean
)
