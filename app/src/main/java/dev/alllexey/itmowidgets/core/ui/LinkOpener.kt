package dev.alllexey.itmowidgets.core.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.View
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.util.HttpsNavigationPolicy
import dev.alllexey.itmowidgets.core.util.TelegramLinks

/** Opens an https link outside the app (t.me in Telegram); without a handler a snackbar on [anchor] says so. */
fun Fragment.openLink(url: String, anchor: View) {
    if (!HttpsNavigationPolicy.isNavigable(url)) {
        Snackbar.make(anchor, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
        return
    }
    // Telegram links go straight to the client; without it the https page is the fallback.
    TelegramLinks.deepLink(url)?.let { deepLink ->
        try {
            startActivity(Intent(Intent.ACTION_VIEW, deepLink.toUri()))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
    } catch (_: ActivityNotFoundException) {
        Snackbar.make(anchor, R.string.link_open_failed, Snackbar.LENGTH_SHORT).show()
    }
}
