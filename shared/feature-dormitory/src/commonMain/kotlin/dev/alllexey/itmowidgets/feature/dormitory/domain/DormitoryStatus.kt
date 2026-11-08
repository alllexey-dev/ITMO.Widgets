package dev.alllexey.itmowidgets.feature.dormitory.domain

import dev.alllexey.itmowidgets.core.text.RichText
import kotlin.time.Instant

/** Where a student is in getting and keeping a dormitory place (GLOSSARY.md, Этап общежития). */
enum class DormitoryStage {
    /** `В очереди`. */
    QUEUE,

    /** `Место предоставлено`: a settlement date must be picked within a limited window. */
    PLACE_OFFERED,

    /** `Дата заселения выбрана`. */
    DATE_CHOSEN,

    /** `Заселён`. */
    SETTLED;

    /** Документы для заселения matter only before the student settles in. */
    val needsDocuments: Boolean get() = this == PLACE_OFFERED || this == DATE_CHOSEN
}

/** The dormitory ITMO assigned; [instructions] are the settlement instructions from ITMO's HTML as safe text. */
data class AssignedDormitory(val name: String, val address: String, val instructions: RichText)

/** One Документ для заселения: its name, ITMO's status label and whether ITMO confirmed it. */
data class SettlementDocument(val name: String, val status: String, val confirmed: Boolean)

/**
 * A student's dormitory application: [stage] is null for a stage this version does not know, [queuePlace] is set in
 * the queue; [documents] are read only while the stage needs them. [placeOfferedSeenAt] is when the app first saw
 * `Место предоставлено`, set only in that stage: ITMO gives no time of its own and the app computes no deadline.
 */
data class DormitoryStatus(
    val stage: DormitoryStage?,
    val queuePlace: Int?,
    val dormitory: AssignedDormitory?,
    val documents: List<SettlementDocument>,
    val placeOfferedSeenAt: Instant? = null,
)

/** The last successful answer from my.itmo and when it came; [status] is null for a student without an application. */
data class DormitoryAnswer(val status: DormitoryStatus?, val fetchedAt: Instant)
