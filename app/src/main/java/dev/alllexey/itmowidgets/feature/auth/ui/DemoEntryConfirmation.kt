package dev.alllexey.itmowidgets.feature.auth.ui

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast

/**
 * Confirms the hidden demo entry the Android way: a haptic on [view] and a toast with [message]. The Compose shell's
 * auth entry calls it.
 */
fun confirmDemoEntry(context: Context, view: View?, message: String) {
    val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    view?.performHapticFeedback(confirm)
    Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
}
