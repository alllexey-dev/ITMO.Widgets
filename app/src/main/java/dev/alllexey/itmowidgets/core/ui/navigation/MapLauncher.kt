package dev.alllexey.itmowidgets.core.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.alllexey.itmowidgets.core.location.MapDestination

/** Opens a destination in whatever handles `geo:` URIs; false when nothing does. */
object MapLauncher {
    fun open(context: Context, destination: MapDestination): Boolean {
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, destination.geoUri().toUri()))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
