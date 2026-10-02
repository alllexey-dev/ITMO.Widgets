package dev.alllexey.itmowidgets.feature.qr.ui

/** The part of `TileService` a tap needs; the service itself implements it. */
interface QrTileHost {
    fun isLocked(): Boolean

    fun unlockAndRun(action: Runnable)

    fun openPass()
}

object QrTileClick {

    /** On the lock screen the pass opens only after the user unlocks the device. */
    fun handle(host: QrTileHost) {
        if (host.isLocked()) host.unlockAndRun { host.openPass() } else host.openPass()
    }
}

/** How a tile starts an activity: the `Intent` overload throws on Android 14+ for apps targeting it. */
enum class QrTileLaunch { PENDING_INTENT, INTENT }

fun qrTileLaunchFor(sdkInt: Int): QrTileLaunch =
    if (sdkInt >= 34) QrTileLaunch.PENDING_INTENT else QrTileLaunch.INTENT
