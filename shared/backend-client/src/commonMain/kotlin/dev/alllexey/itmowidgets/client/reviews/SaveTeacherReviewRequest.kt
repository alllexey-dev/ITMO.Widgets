package dev.alllexey.itmowidgets.client.reviews

import kotlinx.serialization.Serializable

/**
 * Creates or edits the caller's review (`PUT /api/teachers/{isu}/reviews/mine`).
 *
 * [anonymous] defaults to `true` and is always encoded: a body without it must never reveal the author's name.
 * Backend's limits (not checked here, so an app cannot crash on user input): [text] has 30 to 3000 characters after
 * trimming, [subjectTitle] at most 200 (blank becomes `null`), [flowIds] at most 50 positive numbers. A `null`
 * [subjectTitle] is omitted from the body.
 *
 * @property flowIds Candidate ISU flows from the author's schedule history; Backend checks them against ISU and
 *   never trusts them.
 */
@Serializable
data class SaveTeacherReviewRequest(
    val subjectTitle: String? = null,
    val text: String,
    val anonymous: Boolean = true,
    val flowIds: List<Long> = emptyList(),
)
