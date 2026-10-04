package dev.alllexey.itmowidgets.feature.settings.ui

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import androidx.annotation.RequiresApi
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.QuickSettingsTiles
import dev.alllexey.itmowidgets.core.ui.withAppLocale
import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult

/** Shows the system dialog that adds the QR pass tile; the system may stop showing it after repeated refusals. */
@RequiresApi(33)
fun Activity.requestAddQrTile(onResult: (QrTileAddResult) -> Unit) {
    getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(this, QuickSettingsTiles.QR_PASS),
        withAppLocale().getString(R.string.qr_tile_label),
        Icon.createWithResource(this, R.drawable.ic_tile_qr),
        mainExecutor
    ) { code -> onResult(qrTileAddResultOf(code)) }
}

fun qrTileAddResultOf(code: Int): QrTileAddResult = when (code) {
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> QrTileAddResult.ADDED
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> QrTileAddResult.ALREADY_ADDED
    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> QrTileAddResult.NOT_ADDED
    StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS -> QrTileAddResult.IN_PROGRESS
    else -> QrTileAddResult.FAILED
}
