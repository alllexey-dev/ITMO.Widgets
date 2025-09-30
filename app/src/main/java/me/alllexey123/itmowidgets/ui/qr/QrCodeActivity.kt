package me.alllexey123.itmowidgets.ui.qr

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import me.alllexey123.itmowidgets.ItmoWidgetsApp
import me.alllexey123.itmowidgets.R

class QrCodeActivity : AppCompatActivity() {

    private lateinit var qrBgImage: ImageView

    private lateinit var qrCodeImage: ImageView

    private lateinit var fabRefresh: FloatingActionButton

    private val qrCodeViewModel: QrCodeViewModel by viewModels {
        val appContainer = (application as ItmoWidgetsApp).appContainer
        QrCodeViewModelFactory(appContainer.qrCodeRepository)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code)
        qrCodeImage = findViewById(R.id.qr_code_image)
        qrBgImage = findViewById(R.id.qr_bg_image)
        setupButtons()

        observeUiState()
        updateQr()
    }

    private fun setupButtons() {
        fabRefresh = findViewById(R.id.fab_refresh)
        fabRefresh.setOnClickListener {
            val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
            appContainer.qrCodeRepository.clearCache()
            updateQr()
        }
    }

    fun updateQr() {
        qrCodeViewModel.fetchQrCode()
    }

    private fun observeUiState() {
        qrCodeViewModel.uiState.observe(this) { state ->
            val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
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
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    renderer.renderEmpty(dynamic = dynamicColors)
                }
            }

            val colors = renderer.getQrColors(dynamicColors)
            qrBgImage.setColorFilter(colors.first)
            qrCodeImage.setImageBitmap(bitmap)
        }
    }

}