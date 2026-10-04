package dev.alllexey.itmowidgets.core.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/** Android 13 and newer confirm a copy with their own overlay; older versions need the app's message. */
fun copyNeedsConfirmation(sdkInt: Int): Boolean = sdkInt < Build.VERSION_CODES.TIRAMISU

/**
 * Puts [text] on the clipboard; [confirm] runs only where the system shows no confirmation.
 *
 * @return false when the device has no clipboard and nothing was copied.
 */
fun Context.copyToClipboard(label: CharSequence, text: CharSequence, confirm: () -> Unit): Boolean {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (copyNeedsConfirmation(Build.VERSION.SDK_INT)) confirm()
    return true
}

/**
 * The first clipboard item as text when the clip is plain text or a URI list, else null.
 *
 * Android hands the clipboard only to a window that has focus.
 */
fun Context.clipboardText(): String? {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
    val description = clipboard.primaryClipDescription ?: return null
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
        !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) return null
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
}
