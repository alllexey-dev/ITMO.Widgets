package dev.alllexey.itmowidgets.core.platform

import kotlinx.coroutines.flow.Flow

/**
 * The app coming back to the foreground: on Android the process starting again after all its activities stopped, on
 * iOS the app becoming active. A sheet, a dialog or a pushed screen closing is not a return, so a view model that
 * revalidates on [returns] does it once per return, whichever screen shows.
 */
fun interface AppForeground {
    /** One value per return after collection starts; none for the state the app is in at that moment. */
    fun returns(): Flow<Unit>
}
