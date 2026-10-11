package dev.alllexey.itmowidgets.feature.update.ui


/** Nothing is downloaded in the background, so there is nothing to report. */
class GithubInstallStateWatcher : InstallStateWatcher {
    override fun start(onDownloaded: () -> Unit) = Unit

    override fun stop() = Unit

    override fun completeUpdate() = Unit
}
