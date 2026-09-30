package dev.alllexey.itmowidgets.core.navigation

import android.os.Bundle

/**
 * Arguments of the recordbook subject page. A notification carries them in its intent, so [from] trusts nothing:
 * a malformed or partial set opens the recordbook root instead of a broken page.
 */
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

    fun toBundle(): Bundle = Bundle().apply {
        putLong(ENTRY_ID, entryId)
        putLong(PROGRAM_ID, programId)
        putInt(SEMESTER, semester)
        putString(STUDY_YEAR_KEY, studyYear)
        barsPlan?.let { putLong(BARS_PLAN, it) }
        barsType?.let { putString(BARS_TYPE, it) }
        barsIdentifier?.let { putString(BARS_IDENTIFIER, it) }
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

        fun from(bundle: Bundle?): RecordbookSubjectArgs? {
            if (bundle == null) return null
            return RecordbookSubjectArgs(
                entryId = bundle.getLong(ENTRY_ID),
                programId = bundle.getLong(PROGRAM_ID),
                semester = bundle.getInt(SEMESTER),
                studyYear = bundle.getString(STUDY_YEAR_KEY).orEmpty(),
                barsPlan = if (bundle.containsKey(BARS_PLAN)) bundle.getLong(BARS_PLAN) else null,
                barsType = bundle.getString(BARS_TYPE),
                barsIdentifier = bundle.getString(BARS_IDENTIFIER)
            ).validOrNull()
        }
    }
}
