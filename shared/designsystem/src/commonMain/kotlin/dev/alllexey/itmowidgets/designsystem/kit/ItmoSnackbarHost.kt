package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.delay

/**
 * The app's snackbar host: M3's [SnackbarHost] that keeps a snackbar [MESSAGE_MILLIS] without an action and
 * [ACTION_MILLIS] with one (`Отменить`, `Повторить`), whatever duration the caller asked for, so a confirmation never
 * lingers; an indefinite one stays until it is answered. The accessibility services may ask for longer.
 */
@Composable
fun ItmoSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val current = state.currentSnackbarData
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(current) {
        if (current == null || current.visuals.duration == SnackbarDuration.Indefinite) return@LaunchedEffect
        val action = current.visuals.actionLabel != null
        val millis = if (action) ACTION_MILLIS else MESSAGE_MILLIS
        delay(accessibility?.calculateRecommendedTimeoutMillis(millis, containsIcons = true, containsText = true, containsControls = action) ?: millis)
        current.dismiss()
    }
    SnackbarHost(state, modifier)
}

/** A plain confirmation (`Секция выключена`). */
private const val MESSAGE_MILLIS = 2_000L

/** A snackbar with an action: long enough to reach the button. */
private const val ACTION_MILLIS = 5_000L
