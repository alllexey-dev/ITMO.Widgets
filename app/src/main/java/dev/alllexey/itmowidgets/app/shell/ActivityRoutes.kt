package dev.alllexey.itmowidgets.app.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import dev.alllexey.itmowidgets.core.navigation.ActivityRoute
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropContract
import dev.alllexey.itmowidgets.core.ui.spoiler.SpoilerCropResult
import dev.alllexey.itmowidgets.feature.auth.ui.LoginActivity
import dev.alllexey.itmowidgets.feature.recordbook.ui.BarsLoginActivity

/**
 * The three Activities the Compose shell keeps as Activities, with their FQCNs (route map section 6): they are never
 * back-stack keys. The shell starts an entry route's [ActivityRoute] above everything; the entries launch them for a
 * result through these intents and the spoiler contract.
 */
object ActivityRoutes {

    /** The plain start of an entry route's activity, as `MainActivity.applyRoute` does today. */
    fun intent(context: Context, route: ActivityRoute): Intent = when (route) {
        ActivityRoute.BARS_LOGIN -> barsLogin(context)
    }

    /** ITMO ID sign-in, for a result to the auth screen. */
    fun login(context: Context): Intent = Intent(context, LoginActivity::class.java)

    /** BARS sign-in, for a result to the recordbook screens, or plainly from `ACTION_OPEN_BARS_LOGIN`. */
    fun barsLogin(context: Context): Intent = Intent(context, BarsLoginActivity::class.java)

    /** Picks and crops a spoiler image in `SpoilerCropActivity`; settings and the first-run flow reach it. */
    fun spoilerCrop(): ActivityResultContract<Uri, SpoilerCropResult> = SpoilerCropContract()
}
