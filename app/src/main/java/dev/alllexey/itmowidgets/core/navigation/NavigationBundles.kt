package dev.alllexey.itmowidgets.core.navigation

import android.os.Bundle
import androidx.core.os.bundleOf
import kotlinx.serialization.json.Json

/*
 * The Android `Bundle` side of the navigation arguments, kept out of the argument classes so they stay
 * platform-free. The keys are the ones notifications, saved state and the Fragment hosts already use.
 */

fun RecordbookSubjectArgs.toBundle(): Bundle = Bundle().apply {
    putLong(RecordbookSubjectArgs.ENTRY_ID, entryId)
    putLong(RecordbookSubjectArgs.PROGRAM_ID, programId)
    putInt(RecordbookSubjectArgs.SEMESTER, semester)
    putString(RecordbookSubjectArgs.STUDY_YEAR_KEY, studyYear)
    barsPlan?.let { putLong(RecordbookSubjectArgs.BARS_PLAN, it) }
    barsType?.let { putString(RecordbookSubjectArgs.BARS_TYPE, it) }
    barsIdentifier?.let { putString(RecordbookSubjectArgs.BARS_IDENTIFIER, it) }
}

/** The arguments [bundle] describes; null for a missing bundle or a set [RecordbookSubjectArgs.validOrNull] refuses. */
fun RecordbookSubjectArgs.Companion.from(bundle: Bundle?): RecordbookSubjectArgs? {
    if (bundle == null) return null
    return RecordbookSubjectArgs(
        entryId = bundle.getLong(RecordbookSubjectArgs.ENTRY_ID),
        programId = bundle.getLong(RecordbookSubjectArgs.PROGRAM_ID),
        semester = bundle.getInt(RecordbookSubjectArgs.SEMESTER),
        studyYear = bundle.getString(RecordbookSubjectArgs.STUDY_YEAR_KEY).orEmpty(),
        barsPlan = if (bundle.containsKey(RecordbookSubjectArgs.BARS_PLAN)) {
            bundle.getLong(RecordbookSubjectArgs.BARS_PLAN)
        } else {
            null
        },
        barsType = bundle.getString(RecordbookSubjectArgs.BARS_TYPE),
        barsIdentifier = bundle.getString(RecordbookSubjectArgs.BARS_IDENTIFIER)
    ).validOrNull()
}

fun SheetScoresArgs.toBundle(): Bundle = bundleOf(
    SheetScoresArgs.SUBJECT_ID to subjectId,
    SheetScoresArgs.SUBJECT_NAME to subjectName,
    SheetScoresArgs.PERIOD_KEY to periodKey,
    SheetScoresArgs.URL to url,
    SheetScoresArgs.STEP to step.name,
)

/** [args] as the one kotlinx JSON string a sheet keeps under its argument key. */
inline fun <reified T> encodeNavigationArgs(args: T): String = Json.encodeToString(args)

inline fun <reified T> decodeNavigationArgs(json: String): T = Json.decodeFromString(json)

inline fun <reified T> Bundle.putNavigationArgs(key: String, args: T) = putString(key, encodeNavigationArgs(args))

/** The arguments [putNavigationArgs] stored under [key]; null when there are none. */
inline fun <reified T> Bundle.navigationArgs(key: String): T? = getString(key)?.let { decodeNavigationArgs<T>(it) }
