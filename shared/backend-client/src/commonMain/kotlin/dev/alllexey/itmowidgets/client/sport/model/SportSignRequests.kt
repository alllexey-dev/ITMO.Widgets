package dev.alllexey.itmowidgets.client.sport.model

import kotlinx.serialization.Serializable

/**
 * Joins the free-sign queue of a real lesson. With [forceSign] the entry stays eligible until the lesson ends;
 * without it, until one hour before the start (Backend's rule).
 */
@Serializable
data class SportFreeSignRequest(
    val lessonId: Long,
    val forceSign: Boolean,
)

/** Joins the auto-sign queue of a weekly prototype lesson. */
@Serializable
data class SportAutoSignRequest(
    val prototypeLessonId: Long,
)
