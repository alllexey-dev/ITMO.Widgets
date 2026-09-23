package dev.alllexey.itmowidgets.feature.resources.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.View
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.HttpsNavigationPolicy

/** Opens an https link outside the app; without a handler a snackbar on [anchor] says so. */
fun Fragment.openLink(url: String, anchor: View) {
    if (!HttpsNavigationPolicy.isNavigable(url)) {
        Snackbar.make(anchor, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        Snackbar.make(anchor, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
    }
}
