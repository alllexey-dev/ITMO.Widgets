package dev.alllexey.itmowidgets.feature.qr.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeUiState

/** A synthetic pass: the payload LH-0's references and the generator goldens use. */
private val PreviewPass = QrCodeSnapshot("ITMO-TEST", 3_600_000)

@Preview
@Composable
private fun QrPassScreenContentPreview() = ItmoPreview {
    QrPassScreen(QrCodeUiState.Content(PreviewPass), onRefresh = {}, onBack = {})
}

@Preview
@Composable
private fun QrPassScreenRefreshingPreview() = ItmoPreview {
    QrPassScreen(QrCodeUiState.Content(PreviewPass, refreshing = true), onRefresh = {}, onBack = {})
}

@Preview
@Composable
private fun QrPassScreenLoadingPreview() = ItmoPreview {
    QrPassScreen(QrCodeUiState.Loading, onRefresh = {}, onBack = {})
}

@Preview
@Composable
private fun QrPassScreenEmptyPreview() = ItmoPreview {
    QrPassScreen(QrCodeUiState.Empty, onRefresh = {}, onBack = {})
}

@Preview
@Composable
private fun QrPassScreenErrorPreview() = ItmoPreview {
    QrPassScreen(QrCodeUiState.Error(AppError.Network), onRefresh = {}, onBack = {})
}
