package dev.alllexey.itmowidgets.client

import kotlin.coroutines.cancellation.CancellationException

/**
 * Who built the app (ADR 0034). [channel] is the distribution (`github`, `play`, `appstore`, `testflight`,
 * `sideload`, `dev`) or `custom` for an unofficial build; [source] the building repository `owner/name` or `local`,
 * [commit] the git commit of the build, [base] the upstream version a custom build is based on. The build scripts
 * put them into Android's `BuildConfig` and the iOS `Info.plist`; `null` when the build does not say.
 */
data class BuildIdentity(
    val channel: String,
    val source: String? = null,
    val commit: String? = null,
    val base: String? = null,
)

/**
 * The client as Backend records it (ADR 0034), sent with every request as [HEADER]:
 * `kind=android; version=2.3.0; build=20300; channel=play; os=36; install=<uuid>`.
 *
 * [kind] is `android` or `ios`, [version] and [build] are as in [ClientVersion], [os] the Android API level or the
 * iOS major version. [installId] is asked once per request for the random id of this install; `null`, or a
 * failure, leaves `install` out. Values keep only `[A-Za-z0-9._/+-]` and at most 64 characters; an empty value
 * leaves its key out, and a pair that would make the header longer than 512 characters is left out too.
 */
class ClientHeader(
    kind: String,
    version: String,
    build: String,
    identity: BuildIdentity,
    os: String,
    private val installId: suspend () -> String? = { null },
) {
    private val fixed = listOf(
        "kind" to kind,
        "version" to version,
        "build" to build,
        "channel" to identity.channel,
        "source" to identity.source,
        "commit" to identity.commit,
        "base" to identity.base,
        "os" to os,
    )

    suspend fun value(): String = format(fixed + ("install" to install()))

    private suspend fun install(): String? =
        try {
            installId()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null // the header never fails a request
        }

    companion object {
        const val HEADER = "X-Client"
        const val MAX_LENGTH = 512
        const val MAX_VALUE_LENGTH = 64

        /** [pairs] as `key=value; ...` in their order, sanitized, skipping empty values and what does not fit. */
        fun format(pairs: List<Pair<String, String?>>): String =
            pairs.fold("") { header, (key, raw) ->
                val value = raw.orEmpty().filter(::allowed).take(MAX_VALUE_LENGTH)
                val next = if (header.isEmpty()) "$key=$value" else "$header; $key=$value"
                if (value.isEmpty() || next.length > MAX_LENGTH) header else next
            }

        private fun allowed(char: Char): Boolean =
            char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "._/+-"
    }
}
