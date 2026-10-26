package dev.alllexey.itmowidgets.feature.wifi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials
import dev.alllexey.itmowidgets.feature.wifi.presentation.WifiStatus
import dev.alllexey.itmowidgets.feature.wifi.presentation.WifiUiState

/** Synthetic credentials; no real account. */
private val Sample = WifiCredentials(login = "123456", password = "12345678")

internal class WifiSheetHiddenPreview {
    @Preview(name = "hidden")
    @Composable
    fun WifiSheet() = SheetSurface { WifiSheetContent(WifiUiState(Sample, status = WifiStatus.Ready()), WifiActions()) }
}

internal class WifiSheetShownPreview {
    @Preview(name = "shown")
    @Composable
    fun WifiSheet() = SheetSurface {
        WifiSheetContent(WifiUiState(Sample, passwordShown = true, status = WifiStatus.Ready()), WifiActions())
    }
}

internal class WifiSheetNoPasswordPreview {
    @Preview(name = "no-password")
    @Composable
    fun WifiSheet() = SheetSurface { WifiSheetContent(WifiUiState(status = WifiStatus.NoPassword), WifiActions()) }
}

internal class WifiSheetSignInPreview {
    @Preview(name = "sign-in")
    @Composable
    fun WifiSheet() = SheetSurface { WifiSheetContent(WifiUiState(status = WifiStatus.SignInRequired), WifiActions()) }
}

internal class WifiSheetErrorPreview {
    @Preview(name = "error")
    @Composable
    fun WifiSheet() = SheetSurface {
        WifiSheetContent(WifiUiState(status = WifiStatus.Failed(AppError.Network)), WifiActions())
    }
}

internal class WifiSheetLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun WifiSheet() = SheetSurface { WifiSheetContent(WifiUiState(), WifiActions()) }
}

/** The sheet's colour under the body, as the shell's sheet draws it. */
@Composable
private fun SheetSurface(content: @Composable () -> Unit) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) { content() }
}
