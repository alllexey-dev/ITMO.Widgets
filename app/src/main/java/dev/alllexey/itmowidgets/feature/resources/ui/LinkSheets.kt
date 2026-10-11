package dev.alllexey.itmowidgets.feature.resources.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.core.os.bundleOf
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.ui.clipboardText
import dev.alllexey.itmowidgets.core.ui.copyToClipboard
import dev.alllexey.itmowidgets.core.url.HttpsNavigationPolicy
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel

/** The arguments every links sheet reads through its view model's `SavedStateHandle`. */
internal fun SubjectLinksArgs.toArguments(linkId: String? = null): Bundle = bundleOf(
    SubjectLinksArgs.SUBJECT_ID to subjectId,
    SubjectLinksArgs.SUBJECT_NAME to subjectName,
    SubjectLinksArgs.PERIOD_KEY to periodKey,
).apply { linkId?.let { putString(SubjectLinksArgs.LINK_ID, it) } }

internal fun ResourceScope.toArgs() = SubjectLinksArgs(subjectId, subjectName, periodKey)

/** Copies a link's [url]; below Android 13 a toast confirms it. False when the device has no clipboard. */
internal fun Context.copySubjectLink(url: String): Boolean =
    copyToClipboard(getString(R.string.links_copy), url) {
        Toast.makeText(this, R.string.links_copied, Toast.LENGTH_SHORT).show()
    }

/**
 * Only a single https link the app would open goes into an empty address field. Clipboard reads need window focus,
 * so a new link's sheet calls this once its window first has it.
 */
internal fun LinkEditorViewModel.pasteClipboardLink(context: Context) {
    if (uiState.value.url.isNotEmpty()) return
    val text = context.clipboardText()?.trim() ?: return
    if (text.any(Char::isWhitespace) || !HttpsNavigationPolicy.isNavigable(text)) return
    onUrlChanged(text)
}
