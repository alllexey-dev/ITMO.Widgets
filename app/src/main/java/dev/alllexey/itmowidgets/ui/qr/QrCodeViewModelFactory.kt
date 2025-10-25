package dev.alllexey.itmowidgets.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.alllexey.itmowidgets.util.qr.QrToolkit

class QrCodeViewModelFactory(
    private val qrToolkit: QrToolkit
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QrCodeViewModel::class.java)) {
            return QrCodeViewModel(qrToolkit) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}