package dev.alllexey.itmowidgets.feature.update.ui

import android.app.Activity

/** What «Обновить» does; each distribution (`github`, `play`) has its own. */
interface UpdateAction {
    /**
     * Starts updating to the release Backend reported. [unsupported] means the installed build is below the
     * minimum, so the update is not optional. [onFailed] runs when nothing could be opened.
     */
    fun start(activity: Activity, unsupported: Boolean, onFailed: () -> Unit)
}
