package dev.alllexey.itmowidgets.feature.qr.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import androidx.core.content.ContextCompat
import dev.alllexey.itmowidgets.core.util.color
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.databinding.FragmentQrCodeBinding
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeUiState
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeViewModel
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrToolkit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class QrCodeFragment : Fragment() {
    private var _binding: FragmentQrCodeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: QrCodeViewModel by viewModels()
    @Inject lateinit var toolkit: QrToolkit
    private var renderedHex: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQrCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { closeScreen() }
        binding.refreshButton.setOnClickListener { viewModel.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collectLatest(::render) }
                launch {
                    viewModel.refreshErrors.collect { error ->
                        Snackbar.make(binding.root, error.messageRes(), Snackbar.LENGTH_LONG)
                            .setAction(R.string.common_retry) { viewModel.refresh() }.show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.start()
    }

    override fun onStop() {
        viewModel.stop()
        super.onStop()
    }

    private suspend fun render(state: QrCodeUiState) = with(binding) {
        val content = state as? QrCodeUiState.Content
        qrInstruction.visibility = if (content != null) View.VISIBLE else View.INVISIBLE
        refreshButton.isEnabled = state != QrCodeUiState.Loading && content?.refreshing != true
        refreshButton.icon = if (content?.refreshing == true) {
            val spec = CircularProgressIndicatorSpec(requireContext(), null, 0,
                com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall)
            spec.indicatorColors = intArrayOf(requireContext().color.primary)
            IndeterminateDrawable.createCircularDrawable(requireContext(), spec)
        } else ContextCompat.getDrawable(requireContext(), R.drawable.ic_refresh)
        if (content != null) {
            stateContainer.isVisible = false
            if (renderedHex != content.code.hex) {
                qrImage.isVisible = false
                loading.isVisible = true
                try {
                    val bitmap = withContext(Dispatchers.Default) { toolkit.generateQrBitmap(content.code.hex) }
                    qrImage.setImageBitmap(bitmap)
                    renderedHex = content.code.hex
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    showError(R.string.common_load_error_title, R.string.qr_pass_empty_description)
                    return@with
                }
            }
            qrImage.isVisible = true
            loading.isVisible = false
        } else {
            qrImage.isVisible = false
            qrImage.setImageDrawable(null)
            renderedHex = null
            loading.isVisible = state == QrCodeUiState.Loading
            stateContainer.isVisible = state != QrCodeUiState.Loading
            when (state) {
                QrCodeUiState.Empty -> showError(R.string.qr_pass_empty_title, R.string.qr_pass_empty_description)
                is QrCodeUiState.Error -> showError(R.string.common_load_error_title, state.error.messageRes())
                else -> Unit
            }
        }
    }

    private fun showError(title: Int, description: Int) = with(binding) {
        qrImage.isVisible = false
        qrInstruction.visibility = View.INVISIBLE
        loading.isVisible = false
        stateContainer.isVisible = true
        stateTitle.setText(title)
        stateDescription.setText(description)
        refreshButton.isEnabled = true
    }

    override fun onDestroyView() {
        renderedHex = null
        binding.qrImage.setImageDrawable(null)
        _binding = null
        super.onDestroyView()
    }
}
