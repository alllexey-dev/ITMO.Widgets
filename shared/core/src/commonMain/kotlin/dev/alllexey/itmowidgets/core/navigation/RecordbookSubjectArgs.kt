package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.Serializable

/**
 * Arguments of the recordbook subject page. A notification carries them in its intent, so `from(Bundle)` trusts
 * nothing: a malformed or partial set opens the recordbook root instead of a broken page.
 */
@Serializable
data class RecordbookSubjectArgs(
    val entryId: Long,
    val programId: Long,
    val semester: Int,
    val studyYear: String,
    val barsPlan: Long? = null,
    val barsType: String? = null,
    val barsIdentifier: String? = null
) {
    /** These arguments when they describe a page; null for non-positive ids, an odd study year or half a BARS journal. */
    fun validOrNull(): RecordbookSubjectArgs? {
        if (entryId <= 0 || programId <= 0 || semester <= 0) return null
        if (!STUDY_YEAR.matches(studyYear)) return null
        val journal = listOf(barsPlan, barsType, barsIdentifier)
        if (journal.any { it != null } && journal.any { it == null }) return null
        return this
    }

    companion object {
        const val ENTRY_ID = "entry_id"
        const val PROGRAM_ID = "program_id"
        const val SEMESTER = "semester"
        const val STUDY_YEAR_KEY = "study_year"
        const val BARS_PLAN = "bars_plan"
        const val BARS_TYPE = "bars_type"
        const val BARS_IDENTIFIER = "bars_identifier"

        private val STUDY_YEAR = Regex("""\d{4}/\d{4}""")
    }
}
