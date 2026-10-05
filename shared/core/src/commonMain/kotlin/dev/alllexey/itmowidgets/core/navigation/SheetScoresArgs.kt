package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.Serializable

/**
 * Arguments of the «Мои баллы» sheet: [url] is a link of the subject, the subject period is the link's
 * `ResourceScope`. [Step.CONNECT] connects the sheet behind [url]; [Step.TOTAL] picks another total of the
 * connected one.
 */
@Serializable
data class SheetScoresArgs(
    val subjectId: Long,
    val subjectName: String,
    val periodKey: String,
    val url: String,
    val step: Step,
) {
    enum class Step { CONNECT, TOTAL }

    companion object {
        const val SUBJECT_ID = "sheet_subject_id"
        const val SUBJECT_NAME = "sheet_subject_name"
        const val PERIOD_KEY = "sheet_period_key"
        const val URL = "sheet_url"
        const val STEP = "sheet_step"
    }
}
