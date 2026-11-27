package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data class SubjectLinksArgs(val subjectId: Long, val subjectName: String, val periodKey: String) {
    /** This, when it names a subject period (a positive id, a name, a `YYYY-S` key); null otherwise. */
    fun validOrNull(): SubjectLinksArgs? =
        takeIf { subjectId > 0 && subjectName.isNotBlank() && PERIOD.matches(periodKey) }

    companion object {
        private val PERIOD = Regex("""\d{4}-[12]""")

        const val SUBJECT_ID = "link_subject_id"
        const val SUBJECT_NAME = "link_subject_name"
        const val PERIOD_KEY = "link_period_key"
        /** The link a sheet edits or acts on; absent for a new link. */
        const val LINK_ID = "link_id"
    }
}
