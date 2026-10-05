package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.json.UuidSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A pending browser sign-in shown before the user approves it. Everything but [userAgent] is required.
 *
 * @property challengeId The challenge to pass to [UsersApi.approveWebLogin].
 * @property userAgent The browser's `User-Agent` as Backend recorded it (at most 300 characters), absent or `null`
 *   when the browser sent none.
 * @property expiresAt When the code stops being valid, two minutes after [createdAt].
 */
@Serializable
data class WebLoginPreview(
    @Serializable(with = UuidSerializer::class) val challengeId: Uuid,
    val userAgent: String?,
    @Serializable(with = WireInstantSerializer::class) val createdAt: Instant,
    @Serializable(with = WireInstantSerializer::class) val expiresAt: Instant,
)
