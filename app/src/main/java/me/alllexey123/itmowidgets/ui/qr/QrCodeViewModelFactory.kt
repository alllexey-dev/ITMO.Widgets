package me.alllexey123.itmowidgets.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import me.alllexey123.itmowidgets.data.repository.QrCodeRepository

class QrCodeViewModelFactory(
    private val qrCodeRepository: QrCodeRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(QrCodeViewModel::class.java)) {
            return QrCodeViewModel(qrCodeRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}