package dev.alllexey.itmowidgets.core.ui

import android.content.Intent
import androidx.fragment.app.Fragment

/** The system Sharesheet for plain [text]; [title] heads the sheet and becomes the subject in apps that use one. */
fun shareTextIntent(title: String, text: String): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .putExtra(Intent.EXTRA_TITLE, title),
    null
)

fun Fragment.shareText(title: String, text: String) {
    startActivity(shareTextIntent(title, text))
}
