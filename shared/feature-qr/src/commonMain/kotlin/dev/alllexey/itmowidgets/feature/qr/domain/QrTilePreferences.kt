package dev.alllexey.itmowidgets.feature.qr.domain

/** Remembers whether the QR pass tile is in the quick settings; the system offers no way to ask. */
interface QrTilePreferences {

    suspend fun setAdded(added: Boolean)
}
