package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.error.BackendException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * Users (routes under `/api/users`): profiles, lookup, the viewer's privacy settings and identity, and approving a
 * browser sign-in. Semantics are in Backend's `privacy.md`, `friendships.md` and `web.md` contracts.
 *
 * Every [UserData] carries capabilities computed for the authenticated viewer, never the owner's audiences; every
 * [UserProfile] is relative to the viewer. Not mirrored: `GET /api/users/me/roles` (the web version reads it, the
 * apps do not); `GET /api/users/me/restrictions` belongs to the links area.
 */
interface UsersApi {

    /** `GET /api/users/{isu}`. A self profile is `NONE` with every capability; 404 for an unregistered ISU. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun userProfile(isu: Int): UserProfile

    /**
     * `GET /api/users/{isu}/friends`: the owner's accepted friends, each relative to the viewer. 403 when the
     * owner's friends audience excludes the viewer (`canViewFriends`); pending requests are never listed.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun userFriends(isu: Int): List<UserProfile>

    /** `POST /api/users/lookup`; see [UserLookupRequest] for the limits. Creates no account. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun lookupUsers(request: UserLookupRequest): UserLookupResponse

    /** `GET /api/users/me/privacy`. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun myPrivacySettings(): UserPrivacySettings

    /**
     * `PUT /api/users/me/privacy`: replaces all three audiences and returns the saved settings. Backend answers a
     * missing, `null` or unknown audience with 400 and changes nothing.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun updateMyPrivacySettings(settings: UserPrivacySettings): UserPrivacySettings

    /**
     * `PUT /api/users/me/id-token`: stores the name, picture and groups of the caller's id token. An id token of
     * another ISU is 409 `business_rule_violation`. Backend's confirmation text is not returned.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun updateIdTokenData(request: IdTokenRequest)

    /**
     * `GET /api/users/me/data`: the viewer's own data with every capability `true`; `name` is empty until
     * [updateIdTokenData] ran.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun myUserData(): UserData

    /**
     * `GET /api/users/me/web-login/{code}`: the browser sign-in behind a code typed or scanned in the app; the code
     * is sent as one encoded path segment. 404 `not_found` when it is unknown, used or expired; 403 for a web session.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun webLoginPreview(code: String): WebLoginPreview

    /**
     * `POST /api/users/me/web-login/{challengeId}/approve`. Repeating an approval by the same user succeeds; 404
     * `not_found` when the challenge is gone; 403 for a web session.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun approveWebLogin(challengeId: Uuid)
}
