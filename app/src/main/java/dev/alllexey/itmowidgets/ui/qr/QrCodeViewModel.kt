package dev.alllexey.itmowidgets.ui.qr

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.util.qr.QrToolkit
import kotlinx.coroutines.launch

sealed class QrCodeUiState {
    object Loading : QrCodeUiState()
    data class Success(val qrCodeHex: String) : QrCodeUiState()
    data class Error(val message: String) : QrCodeUiState()
}

class QrCodeViewModel(
    private val qrToolkit: QrToolkit
) : ViewModel()  {

    private val _uiState = MutableLiveData<QrCodeUiState>()
    val uiState: LiveData<QrCodeUiState> = _uiState

    fun fetchQrCode() {
        _uiState.value = QrCodeUiState.Loading

        viewModelScope.launch {
            try {
                _uiState.value = QrCodeUiState.Loading
                val qrCodeHex = qrToolkit.getQrHex()
                _uiState.postValue(QrCodeUiState.Success(qrCodeHex))
            } catch (e: Exception) {
                val errorMessage = "Failed to update qr code: ${e.message}"
                _uiState.postValue(QrCodeUiState.Error(errorMessage))
                e.printStackTrace()
            }
        }
    }
}