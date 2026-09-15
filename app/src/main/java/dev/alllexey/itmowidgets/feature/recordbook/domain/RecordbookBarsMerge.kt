package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject

/** Lays BARS journals over the MyITMO list of the same period; identities, teachers and sport stay MyITMO. */
object RecordbookBarsMerge {
    fun apply(subjects: List<RecordbookSubject>, journals: List<RecordbookSubject>): List<RecordbookSubject> {
        val byName = journals.groupBy { key(it.name) }
        val ambiguous = subjects.groupBy { key(it.name) }.filterValues { it.size > 1 }.keys
        return subjects.map { subject ->
            val name = key(subject.name)
            val journal = byName[name]?.singleOrNull()?.takeUnless { name in ambiguous }
            if (journal == null) subject else subject.withBars(journal)
        }
    }

    fun key(name: String): String = name.lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ").trim()
}

fun RecordbookSubject.withBars(journal: RecordbookSubject): RecordbookSubject = copy(
    score = journal.score, rate = journal.rate, attempt = journal.attempt, hasDetails = true,
    barsJournal = journal.barsJournal, absent = journal.absent
)
