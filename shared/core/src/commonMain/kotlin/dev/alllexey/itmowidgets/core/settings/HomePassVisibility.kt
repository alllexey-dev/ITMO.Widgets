package dev.alllexey.itmowidgets.core.settings

import kotlinx.coroutines.flow.Flow

/** The device's choice to show the QR pass entry on Today; widgets and shortcuts are independent. */
fun interface HomePassVisibility {
    fun observeHomePassVisible(): Flow<Boolean>
}
