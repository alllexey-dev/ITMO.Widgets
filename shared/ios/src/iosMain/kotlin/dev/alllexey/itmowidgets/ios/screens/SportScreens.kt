package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.platform.PlatformActions
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.ui.UserSportIosRoute
import dev.alllexey.itmowidgets.ios.di.IosKoin
import platform.UIKit.UIViewController

/**
 * Another user's sport (`AppRoutes.UserSport`), as Android's shell hosts it: read-only cards whose building opens
 * in Apple Maps. [name] is the profile's display name for the title.
 */
fun userSportViewController(isu: Int, name: String, onBack: () -> Unit): UIViewController {
    val koin = IosKoin.koin()
    val time = koin.get<AcademicTimeProvider>()
    val actions = koin.get<PlatformActions>()
    return screenController {
        Box(
            Modifier
                .fillMaxSize()
                .background(ItmoTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            UserSportIosRoute(isu, name, time, onBack = onBack, onOpenMap = { actions.openBuilding(it) })
        }
    }
}

/** A booking's building from its room, as Android's `geo:` query: nothing opens when the room names none. */
private fun PlatformActions.openBuilding(booking: SportBooking) {
    val address = booking.extractBuildingAddress() ?: return
    openMap(MapDestination(label = address, address = address))
}
