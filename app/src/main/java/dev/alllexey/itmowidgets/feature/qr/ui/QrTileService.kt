package dev.alllexey.itmowidgets.feature.qr.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.ui.navigation.AppEntryIntentFactory
import dev.alllexey.itmowidgets.feature.qr.presentation.QrTileController
import dev.alllexey.itmowidgets.feature.qr.presentation.QrTileState
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** The «QR-пропуск» quick-settings tile: a tap opens the pass through the same route as the shortcut. */
@AndroidEntryPoint
class QrTileService : TileService(), QrTileHost {

    @Inject
    lateinit var controller: QrTileController

    private val scope = MainScope()

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val state = controller.state()
            qsTile?.apply {
                this.state = if (state == QrTileState.ACTIVE) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                label = getString(R.string.qr_tile_label)
                icon = Icon.createWithResource(this@QrTileService, R.drawable.ic_tile_qr)
                updateTile()
            }
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        controller.onTileAdded()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        controller.onTileRemoved()
    }

    override fun onClick() {
        super.onClick()
        QrTileClick.handle(this)
    }

    // qrTileLaunchFor checks the platform level before the PendingIntent overload is used.
    @SuppressLint("NewApi")
    override fun openPass() {
        val intent = passIntent(this)
        when (qrTileLaunchFor(Build.VERSION.SDK_INT)) {
            QrTileLaunch.PENDING_INTENT -> collapseWith(
                PendingIntent.getActivity(
                    this,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            QrTileLaunch.INTENT -> collapseWith(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun collapseWith(pendingIntent: PendingIntent) {
        startActivityAndCollapse(pendingIntent)
    }

    // Only below Android 14, where the PendingIntent overload does not exist yet.
    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun collapseWith(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE = 7301

        /** The same flags as a widget tap: an open task is brought forward and gets the route in onNewIntent. */
        fun passIntent(context: Context): Intent =
            AppEntryIntentFactory.open(context, AppEntryIntents.ACTION_OPEN_QR_PASS).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
    }
}
