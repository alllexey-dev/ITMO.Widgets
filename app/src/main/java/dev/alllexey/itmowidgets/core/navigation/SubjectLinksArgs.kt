package dev.alllexey.itmowidgets.core.navigation

import java.io.Serializable

data class SubjectLinksArgs(val subjectId: Long, val subjectName: String, val periodKey: String) : Serializable {
    companion object {
        const val SUBJECT_ID = "link_subject_id"
        const val SUBJECT_NAME = "link_subject_name"
        const val PERIOD_KEY = "link_period_key"
        /** The link a sheet edits or acts on; absent for a new link. */
        const val LINK_ID = "link_id"
    }
}
