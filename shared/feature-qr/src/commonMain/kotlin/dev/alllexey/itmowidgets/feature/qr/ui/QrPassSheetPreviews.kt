package dev.alllexey.itmowidgets.feature.qr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassCode
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassHolder
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassStatus
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassUiState
import dev.alllexey.itmowidgets.shared.feature.qr.QrStrings

private val Holder = QrPassHolder(
    PreviewFixtures.LongPersonName,
    UiText.Res(QrStrings.qr_pass_group_isu, listOf("M3205", 123456)),
)
private val Code = QrPassCode("ITMO-TEST", useDynamicColors = false)

internal class QrPassSheetReadyPreview {
    @Preview(name = "ready")
    @Composable
    fun QrPassSheet() = SheetSurface {
        QrPassSheetContent(QrPassUiState(Holder, Code, QrPassStatus.Ready("11:44")), onRefresh = {}, onClose = {})
    }
}

internal class QrPassSheetRefreshingPreview {
    @Preview(name = "refreshing")
    @Composable
    fun QrPassSheet() = SheetSurface {
        QrPassSheetContent(QrPassUiState(Holder, Code, QrPassStatus.Refreshing), onRefresh = {}, onClose = {})
    }
}

internal class QrPassSheetOfflinePreview {
    @Preview(name = "offline")
    @Composable
    fun QrPassSheet() = SheetSurface {
        QrPassSheetContent(QrPassUiState(Holder, Code, QrPassStatus.Offline("11:44")), onRefresh = {}, onClose = {})
    }
}

internal class QrPassSheetLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun QrPassSheet() = SheetSurface {
        QrPassSheetContent(QrPassUiState(Holder), onRefresh = {}, onClose = {})
    }
}

internal class QrPassSheetEmptyPreview {
    @Preview(name = "empty")
    @Composable
    fun QrPassSheet() = SheetSurface {
        QrPassSheetContent(QrPassUiState(Holder, status = QrPassStatus.Empty), onRefresh = {}, onClose = {})
    }
}

internal class QrPassSheetErrorPreview {
    @Preview(name = "error")
    @Composable
    fun QrPassSheet() = SheetSurface {
        val state = QrPassUiState(Holder, status = QrPassStatus.Failed(AppError.Network))
        QrPassSheetContent(state, onRefresh = {}, onClose = {})
    }
}

/** The sheet's colour under the body, as the shell's sheet draws it. */
@Composable
private fun SheetSurface(content: @Composable () -> Unit) = ItmoPreview {
    Box(Modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) { content() }
}
