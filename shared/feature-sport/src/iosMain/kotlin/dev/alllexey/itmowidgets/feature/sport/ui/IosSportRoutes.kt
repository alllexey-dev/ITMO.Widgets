package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.user.UserSportRoute
import org.koin.compose.getKoin
import org.koin.core.parameter.parametersOf

/**
 * Another user's sport as the iOS shell hosts it: [UserSportRoute] with its ViewModel reading [isu] and [name] from the
 * arguments Android's destination gives it (`UserScreenArgs`). `koinViewModel()` would hand the definition the
 * Compose controller's own empty `SavedStateHandle` instead.
 */
@Composable
fun UserSportIosRoute(
    isu: Int,
    name: String,
    time: AcademicTimeProvider,
    onBack: () -> Unit,
    onOpenMap: (SportBooking) -> Unit,
    modifier: Modifier = Modifier,
) {
    val koin = getKoin()
    val handle = remember { SavedStateHandle(mapOf(UserScreenArgs.ISU to isu, UserScreenArgs.NAME to name)) }
    val viewModel = viewModel { koin.get<UserSportViewModel> { parametersOf(handle) } }
    UserSportRoute(time, onBack, onOpenMap, modifier, viewModel)
}
