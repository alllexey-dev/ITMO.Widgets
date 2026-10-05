package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** An unread subject: one per half-year and name for both sources. [notified] is false until a digest named it. */
data class MarkNews(
    val id: String,
    val half: StudyHalf,
    val nameKey: String,
    val name: String,
    val detectedAt: Instant,
    val notified: Boolean
) {
    companion object {
        fun idOf(half: StudyHalf, nameKey: String): String = "${half.key}|$nameKey"
    }
}

/** Arguments of the subject page a tap on the news of one subject opens. */
data class MarkSubjectTarget(
    val programId: Long,
    val semester: Int,
    val studyYear: String,
    val entryId: Long,
    val bars: BarsJournalReference?
)

/** How events become unread subjects. Pure: time comes from the caller. */
object MarkNewsRules {
    const val RETENTION_DAYS = 30L
    const val MAX_NEWS = 100

    /**
     * Drops My ITMO events that only repeat BARS: the marks usually reach BARS first and My ITMO later. An event of
     * subject S goes when the BARS snapshot before this check has exactly one plan named S with the same score and
     * grade as S in [current].
     */
    fun withoutEchoes(events: List<MarkEvent>, current: MyItmoMarkSnapshot, previousBars: BarsMarkSnapshot?): List<MarkEvent> {
        if (previousBars == null) return events
        return events.filterNot { event ->
            if (event.source != MarkSource.MY_ITMO || previousBars.half != event.half || current.half != event.half) {
                return@filterNot false
            }
            val subject = current.subjects.filter { subjectNameKey(it.name) == event.nameKey }.singleOrNull()
            val plan = previousBars.plans.filter { subjectNameKey(it.name) == event.nameKey }.singleOrNull()
            subject != null && plan != null && sameScore(plan.score, subject.score) && rateKey(plan.rate) == rateKey(subject.rate)
        }
    }

    /**
     * One record per half-year and name. A new record takes its name from the first event, My ITMO before BARS; an
     * existing one keeps its name. [notify] (a background check) makes the record wait for a digest again; without it
     * (a list the user is looking at) the record counts as delivered.
     */
    fun merge(news: List<MarkNews>, events: List<MarkEvent>, detectedAt: Instant, notify: Boolean): List<MarkNews> {
        if (events.isEmpty()) return news
        val byId = news.associateByTo(LinkedHashMap()) { it.id }
        events.sortedBy { it.source }.forEach { event ->
            val id = MarkNews.idOf(event.half, event.nameKey)
            val existing = byId[id]
            byId[id] = existing?.copy(detectedAt = detectedAt, notified = !notify)
                ?: MarkNews(id, event.half, event.nameKey, event.subjectName, detectedAt, notified = !notify)
        }
        return byId.values.toList()
    }

    /** Without records older than [RETENTION_DAYS] and beyond the [MAX_NEWS] newest, newest first. */
    fun pruned(news: List<MarkNews>, now: Instant): List<MarkNews> {
        val oldest = now - RETENTION_DAYS.days
        return news.filter { it.detectedAt >= oldest }
            .sortedWith(compareByDescending<MarkNews> { it.detectedAt }.thenBy { it.name })
            .take(MAX_NEWS)
    }

    /**
     * The subject page for [news]: exactly one My ITMO subject of that half-year and name, otherwise null. The BARS
     * journal joins when [withBars] (the BARS chip is on) and exactly one plan has that name.
     */
    fun target(news: MarkNews, myItmo: MyItmoMarkSnapshot?, bars: BarsMarkSnapshot?, withBars: Boolean): MarkSubjectTarget? {
        if (myItmo == null || myItmo.half != news.half) return null
        val subject = myItmo.subjects.filter { subjectNameKey(it.name) == news.nameKey }.singleOrNull() ?: return null
        val plan = bars?.takeIf { withBars && it.half == news.half }
            ?.plans?.filter { subjectNameKey(it.name) == news.nameKey }?.singleOrNull()
        return MarkSubjectTarget(
            programId = subject.programId,
            semester = subject.semester,
            studyYear = news.half.studyYear,
            entryId = subject.entryId,
            bars = plan?.let { BarsJournalReference(it.planId, it.type, it.identifier, news.half.yearStart, news.half.half) }
        )
    }
}
