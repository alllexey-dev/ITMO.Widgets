package dev.alllexey.itmowidgets.ui.qr

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R

class QrCodeFragment : Fragment(R.layout.fragment_qr) {

    private lateinit var qrCodeImage: ImageView
    private lateinit var fabRefresh: FloatingActionButton

    private val qrCodeViewModel: QrCodeViewModel by viewModels {
        val appContainer = (requireActivity().application as ItmoWidgetsApp).appContainer
        QrCodeViewModelFactory(appContainer.qrToolkit)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        qrCodeImage = view.findViewById(R.id.qr_code_image)
        fabRefresh = view.findViewById(R.id.fab_refresh)

        setupButtons()
        observeUiState()
        updateQr()
    }

    private fun setupButtons() {
        fabRefresh.setOnClickListener {
            val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer
            appContainer.qrToolkit.repository.clearCache()
            updateQr()
        }
    }

    private fun updateQr() {
        qrCodeViewModel.fetchQrCode()
    }

    private fun observeUiState() {
        qrCodeViewModel.uiState.observe(viewLifecycleOwner) { state ->
            val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer
            val qrToolkit = appContainer.qrToolkit

            val bitmap = when (state) {
                is QrCodeUiState.Loading -> {
                    qrToolkit.generateEmptyQrBitmap()
                }

                is QrCodeUiState.Success -> {
                    qrToolkit.generateQrBitmap(state.qrCodeHex)
                }

                is QrCodeUiState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    qrToolkit.generateEmptyQrBitmap()
                }
            }
            qrCodeImage.setImageBitmap(bitmap)
        }
    }
}