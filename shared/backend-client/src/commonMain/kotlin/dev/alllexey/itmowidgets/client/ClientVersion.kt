package dev.alllexey.itmowidgets.client

/**
 * The app build that calls Backend, sent with every request as [HEADER]:
 * `<versionName> (<build>); <platform>; <distribution>`, e.g. `2.3.0-beta.1 (20291); android; github`.
 *
 * [versionName] is Android's `versionName` or iOS `CFBundleShortVersionString`, [build] Android's `versionCode` or
 * iOS `CFBundleVersion`, [platform] `android` or `ios`, [distribution] `github`, `play`, `appstore` or `dev`. It names
 * the build only: no user or device data. Characters outside printable ASCII and the separators `(`, `)` and `;` are
 * dropped so the value stays one parseable header; an empty part reads as `unknown`.
 */
class ClientVersion(versionName: String, build: String, platform: String, distribution: String) {

    val headerValue: String =
        "${part(versionName)} (${part(build)}); ${part(platform)}; ${part(distribution)}"

    override fun toString(): String = headerValue

    override fun equals(other: Any?): Boolean = other is ClientVersion && other.headerValue == headerValue

    override fun hashCode(): Int = headerValue.hashCode()

    companion object {
        const val HEADER = "X-App-Version"

        private fun part(value: String): String =
            value.filter { it in '!'..'~' && it !in "();" }.ifEmpty { "unknown" }
    }
}
