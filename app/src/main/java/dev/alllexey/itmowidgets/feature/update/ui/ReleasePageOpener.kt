package dev.alllexey.itmowidgets.feature.update.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri

/** Opens the page a release is downloaded from: GitHub releases or the Google Play card. */
fun interface ReleasePageOpener {
    /** False when no app on the device opens [url]. */
    fun open(activity: Activity, url: String): Boolean
}

class ActivityReleasePageOpener : ReleasePageOpener {
    override fun open(activity: Activity, url: String): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
