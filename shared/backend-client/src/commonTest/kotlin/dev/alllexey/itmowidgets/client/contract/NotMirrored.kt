package dev.alllexey.itmowidgets.client.contract

import io.ktor.http.HttpMethod

/**
 * Backend operations the client deliberately does not mirror. [path] is an OpenAPI template, or a prefix ending in
 * a double star segment that covers every operation below it; [method] `null` means every method. [fixtures] are the
 * vendored files
 * of those operations, `contract/`-relative, a trailing `/` covering a directory; they count as claimed.
 */
class NotMirroredRoute(
    val method: HttpMethod?,
    val path: String,
    val reason: String,
    val fixtures: List<String>,
) {
    fun covers(operation: Operation): Boolean {
        if (method != null && method != operation.method) return false
        return if (path.endsWith("/**")) {
            operation.path.startsWith(path.removeSuffix("**"))
        } else {
            operation.path == path
        }
    }

    fun claims(file: String): Boolean = fixtures.any { if (it.endsWith("/")) file.startsWith(it) else file == it }

    override fun toString(): String = "${method?.value ?: "*"} $path"
}

/** The not-mirrored list of `CONTRACT.md`; [ContractConformanceTest] fails when an entry no longer matches. */
object NotMirrored {
    val all: List<NotMirroredRoute> = listOf(
        NotMirroredRoute(
            method = null,
            path = "/api/moderation/**",
            reason = "CO-02, ADR 0026: no Kotlin consumer; moderators work in Web, which uses /api/admin",
            fixtures = listOf(
                "http/moderation/",
                "requests/ModerationDecisionRequest.json",
                "requests/ModerationSettings.json",
            ),
        ),
        NotMirroredRoute(
            method = null,
            path = "/api/admin/**",
            reason = "Web only: the admin pages",
            fixtures = listOf("http/admin/"),
        ),
        NotMirroredRoute(
            method = null,
            path = "/api/web/**",
            reason = "Web only: the browser sign-in and its session cookie; the apps approve through /api/users",
            fixtures = listOf("http/weblogin/"),
        ),
        NotMirroredRoute(
            method = HttpMethod.Get,
            path = "/api/app/version",
            reason = "the latest Android version as a bare string, kept for 2.0.x; the apps read /api/app/version-info",
            fixtures = listOf("http/app/latestAppVersion.json"),
        ),
        NotMirroredRoute(
            method = HttpMethod.Get,
            path = "/api/users/me/roles",
            reason = "Web only: the apps have no role-dependent screen",
            fixtures = listOf("http/users/myRoles.json"),
        ),
        NotMirroredRoute(
            method = HttpMethod.Post,
            path = "/api/sport/free-sign/entry/{id}/mark-satisfied",
            reason = "by entry ID, kept for released clients; the apps mark entries satisfied by lesson",
            fixtures = listOf("http/sport-free-sign/markSportFreeSignEntrySatisfied.json"),
        ),
        NotMirroredRoute(
            method = HttpMethod.Post,
            path = "/api/sport/auto-sign/entry/{id}/mark-satisfied",
            reason = "by entry ID, kept for released clients; the apps mark entries satisfied by lesson",
            fixtures = listOf("http/sport-auto-sign/markSportAutoSignEntrySatisfied.json"),
        ),
    )
}
