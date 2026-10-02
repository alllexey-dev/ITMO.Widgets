package dev.alllexey.itmowidgets.feature.update.ui

import android.app.Activity
import dev.alllexey.itmowidgets.BuildConfig
import javax.inject.Inject

/** The GitHub build is updated by hand: «Обновить» opens the latest release. */
class GithubUpdateAction @Inject constructor(
    private val pages: ReleasePageOpener
) : UpdateAction {

    override fun start(activity: Activity, unsupported: Boolean, onFailed: () -> Unit) {
        if (!pages.open(activity, BuildConfig.DOWNLOAD_URL)) onFailed()
    }
}
