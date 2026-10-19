package dev.alllexey.itmowidgets.core.platform

/**
 * This build as settings show it, the build identity of docs/building.md: the marketing [version] and the [build]
 * number, the [channel] (the distribution of an official build, [CUSTOM] for any other), the building repository
 * [source] (`<owner>/<repo>` or [LOCAL]) and the [commit], null when unknown. Android fills it from `BuildConfig`,
 * iOS from the Info.plist, as they fill `X-Client`.
 */
data class AppBuild(
    val version: String,
    val build: String,
    val channel: String,
    val source: String? = null,
    val commit: String? = null,
) {
    val official: Boolean get() = channel != CUSTOM

    /** The commit as git abbreviates it, a `-dirty` suffix kept: `a1b2c3d`, `a1b2c3d-dirty`. */
    val shortCommit: String?
        get() = commit?.let { full ->
            val sha = full.substringBefore('-')
            sha.take(SHORT_COMMIT_LENGTH) + full.removePrefix(sha)
        }

    /** Everything for a bug report: `2.3.0 (20292); channel=custom; source=owner/repo; commit=a1b2c3d4e5f6`. */
    fun details(): String = listOfNotNull(
        "$version ($build)",
        "channel=$channel",
        source?.let { "source=$it" },
        commit?.let { "commit=$it" },
    ).joinToString("; ")

    companion object {
        const val CUSTOM = "custom"
        const val LOCAL = "local"
        private const val SHORT_COMMIT_LENGTH = 7
    }
}
