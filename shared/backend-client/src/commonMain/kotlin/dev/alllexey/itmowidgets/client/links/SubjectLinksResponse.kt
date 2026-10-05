package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.json.UuidSerializer
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * The links of one subject and period for the viewer.
 *
 * @property mine The viewer's links in any state, with their current content.
 * @property shared Other owners' links the viewer sees: FLOW links first, then by score descending, then by age;
 *   links with the same normalized URL collapse into one.
 * @property previous At most 10 approved ALL links of MATERIALS, TASKS, RECORDINGS, NOTES and EXAM from earlier
 *   periods of the subject, by score.
 * @property pinnedId The viewer's pin for this subject and period when that link is in one of the lists.
 * @property audiences Every flow of the viewer in the subject and period, by depth, then label: the flows a FLOW
 *   link may go to.
 * @property premoderation Whether ALL links wait for review.
 */
@Serializable
data class SubjectLinksResponse(
    val mine: List<SubjectLink>,
    val shared: List<SubjectLink>,
    val previous: List<SubjectLink>,
    @Serializable(with = UuidSerializer::class) val pinnedId: Uuid?,
    val audiences: List<LinkAudience>,
    val premoderation: Boolean,
)

/**
 * A schedule flow of the subject the viewer can publish a FLOW link to.
 *
 * @property label The flow's schedule name (`ФИЗ ПИИКТ 3.2.1`).
 * @property typeId The schedule lesson type of the flow.
 * @property depth The number of parts of the trailing number of [label] (`3.2.1` is 3; a name without one is 1).
 */
@Serializable
data class LinkAudience(
    val flowId: Long,
    val label: String,
    val typeId: Int,
    val depth: Int,
) {
    init {
        require(depth >= 1) { "A flow depth is at least 1" }
    }
}
