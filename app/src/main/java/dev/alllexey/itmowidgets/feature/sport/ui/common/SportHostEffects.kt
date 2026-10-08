package dev.alllexey.itmowidgets.feature.sport.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.shareTextIntent
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSessionTiming
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportShareTarget

/**
 * What only an Android host does for the sport screens, shared by the Fragment hosts and the Navigation 3 entries
 * until SH-1d2 deletes the Fragments: the `geo:` map, the debug template-lesson notice and the share sheet of a lesson.
 */

/** The map app at [address]'s building; false when no app handles `geo:`. */
fun Context.openSportMap(address: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(address)}".toUri()))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** The details sheet's map: without a map app it says `sport_map_unavailable`. */
fun Context.openSportMapOrSay(address: String) {
    if (!openSportMap(address)) Toast.makeText(this, R.string.sport_map_unavailable, Toast.LENGTH_SHORT).show()
}

/** A debug template lesson sent nothing to the server. */
fun Context.showTemplateLessonNotice() {
    Toast.makeText(this, R.string.debug_sport_lesson_action_disabled, Toast.LENGTH_SHORT).show()
}

/** The share sheet of [item]'s link ([target] picks the lesson's or the prediction's), with its date and time. */
fun Context.shareSportLesson(
    item: SportCommonDetailsArgs,
    target: SportShareTarget,
    shareLinks: ShareLinkFactory,
    time: AcademicTimeProvider,
) {
    val link = when (target) {
        is SportShareTarget.Lesson -> shareLinks.sportLesson(target.lessonId)
        is SportShareTarget.Prediction -> shareLinks.predictedSportLesson(target.prototypeLessonId)
    }
    val timing = SportSessionTiming(DateTexts.parseOffsetInstant(item.start), DateTexts.parseOffsetInstant(item.end), time)
    startActivity(
        shareTextIntent(
            getString(R.string.share_sport_title),
            getString(R.string.share_sport_text, item.sectionName, timing.shareDate(), link),
        ),
    )
}
