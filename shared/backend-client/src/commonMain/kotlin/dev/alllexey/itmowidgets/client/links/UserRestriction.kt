package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.json.FallbackEnumSerializer
import dev.alllexey.itmowidgets.client.json.UuidSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * An active restriction of the viewer ([SubjectLinksApi.myRestrictions]). It blocks mutations, never read access.
 *
 * @property reason The moderator's text, shown to the user as is.
 * @property expiresAt `null` for a permanent restriction.
 */
@Serializable
data class UserRestriction(
    @Serializable(with = UuidSerializer::class) val id: Uuid,
    val capability: RestrictionCapability,
    val reason: String,
    @Serializable(with = WireInstantSerializer::class) val startsAt: Instant,
    @Serializable(with = WireInstantSerializer::class) val expiresAt: Instant?,
)

/**
 * What a restriction blocks. A restricted action is 403 `restricted`. A capability added by a newer Backend decodes
 * as [ALL], the most restrictive value, so this client never offers an action Backend might refuse.
 */
@Serializable(with = RestrictionCapabilitySerializer::class)
enum class RestrictionCapability {
    /** Saving a non-private link. */
    SUBMIT_RESOURCES,

    /** Voting on links and teacher reviews. */
    VOTE,

    /** Reporting links and teacher reviews. */
    REPORT,

    /** Writing teacher reviews. */
    WRITE_REVIEWS,

    /** Every action above. */
    ALL,
}

internal object RestrictionCapabilitySerializer : FallbackEnumSerializer<RestrictionCapability>(
    "RestrictionCapability",
    RestrictionCapability.entries,
    RestrictionCapability.ALL,
)
