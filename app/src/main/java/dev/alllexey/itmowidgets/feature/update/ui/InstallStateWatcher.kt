package dev.alllexey.itmowidgets.feature.update.ui

/**
 * Follows an update downloaded in the background (Google Play's flexible update). The `github` variant
 * downloads nothing itself, so its watcher never reports.
 */
interface InstallStateWatcher {
    /** Calls [onDownloaded] once an update is downloaded, also one that finished while the app was away. */
    fun start(onDownloaded: () -> Unit)

    fun stop()

    /** Installs the downloaded update; the app restarts. */
    fun completeUpdate()
}
