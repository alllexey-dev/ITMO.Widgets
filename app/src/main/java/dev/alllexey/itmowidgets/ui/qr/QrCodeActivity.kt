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

class QrCodeFragment : Fragment(R.layout.fragment_qr_code) {

    private lateinit var qrCodeImage: ImageView
    private lateinit var fabRefresh: FloatingActionButton

    private val qrCodeViewModel: QrCodeViewModel by viewModels {
        val appContainer = (requireActivity().application as ItmoWidgetsApp).appContainer
        QrCodeViewModelFactory(appContainer.qrCodeRepository)
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
            appContainer.qrCodeRepository.clearCache()
            updateQr()
        }
    }

    private fun updateQr() {
        qrCodeViewModel.fetchQrCode()
    }

    private fun observeUiState() {
        qrCodeViewModel.uiState.observe(viewLifecycleOwner) { state ->
            val appContainer = (requireContext().applicationContext as ItmoWidgetsApp).appContainer
            val generator = appContainer.qrCodeGenerator
            val renderer = appContainer.qrBitmapRenderer
            val storage = appContainer.storage

            val dynamicColors = storage.getDynamicQrColorsState()

            val bitmap = when (state) {
                is QrCodeUiState.Loading -> {
                    renderer.renderFull(dynamic = dynamicColors)
                }

                is QrCodeUiState.Success -> {
                    val qrCode = generator.generate(state.qrCodeHex)
                    val qrCodeBooleans = generator.toBooleans(qrCode)
                    renderer.render(qrCodeBooleans, dynamic = dynamicColors)
                }

                is QrCodeUiState.Error -> {
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    renderer.renderEmpty(dynamic = dynamicColors)
                }
            }
            qrCodeImage.setImageBitmap(bitmap)
        }
    }
}