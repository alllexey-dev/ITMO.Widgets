package dev.alllexey.itmowidgets.feature.weblogin.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.expandToContent
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.databinding.SheetWebLoginBinding
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginUiState
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Approves a browser's sign-in to the web version with a scanned QR or a typed code. */
@AndroidEntryPoint
class WebLoginBottomSheet : BottomSheetDialogFragment() {
    private var _binding: SheetWebLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WebLoginViewModel by viewModels()
    /** Programmatic updates of the field must not read as typing. */
    private var rendering = false
    private var shownState: Class<out WebLoginUiState>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetWebLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        @Suppress("DEPRECATION")
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        expandToContent()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        code.doAfterTextChanged { if (!rendering) viewModel.onCodeChanged(it?.toString().orEmpty()) }
        code.setOnEditorActionListener { _, action, _ ->
            (action == EditorInfo.IME_ACTION_GO).also { if (it) viewModel.submit() }
        }
        scanButton.setOnClickListener { scan() }
        continueButton.setOnClickListener { viewModel.submit() }
        approveButton.setOnClickListener { viewModel.approve() }
        cancelButton.setOnClickListener { viewModel.editCode() }
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(state: WebLoginUiState) = with(binding) {
        rendering = true
        try {
            val input = state as? WebLoginUiState.Input
            val checking = state as? WebLoginUiState.Checking
            val confirm = state as? WebLoginUiState.Confirm
            inputGroup.isVisible = input != null || checking != null
            confirmGroup.isVisible = confirm != null
            resultGroup.isVisible = state is WebLoginUiState.Done || state is WebLoginUiState.Error

            val text = input?.code ?: checking?.code
            if (text != null && code.text?.toString() != text) {
                code.setText(text)
                code.setSelection(text.length)
            }
            codeLayout.error = input?.error?.resolve(requireContext())
            code.isEnabled = checking == null
            scanButton.isEnabled = checking == null
            continueButton.isEnabled = input?.canSubmit == true
            continueButton.showProgress(checking != null)

            if (confirm != null) {
                browser.text = confirm.browser.resolve(requireContext())
                requestedAt.text = confirm.requestedAt.resolve(requireContext())
                approveButton.isEnabled = !confirm.approving
                approveButton.showProgress(confirm.approving)
                cancelButton.isEnabled = !confirm.approving
            }

            when (state) {
                WebLoginUiState.Done -> bindResult(R.drawable.ic_check_circle, getString(R.string.web_login_done),
                    R.string.common_close) { dismiss() }
                is WebLoginUiState.Error -> bindResult(R.drawable.ic_error_rounded, state.text.resolve(requireContext()),
                    R.string.common_retry) { viewModel.retry() }
                else -> Unit
            }
        } finally {
            rendering = false
        }
        // A new step changes the sheet's height; a keystroke or progress does not.
        if (shownState != state::class.java) {
            shownState = state::class.java
            root.post { if (_binding != null) expandToContent() }
        }
    }

    private fun bindResult(icon: Int, text: String, action: Int, onClick: () -> Unit) = with(binding) {
        resultIcon.setImageResource(icon)
        resultText.text = text
        resultButton.setText(action)
        resultButton.setOnClickListener { onClick() }
    }

    /** In-button progress keeps the button's size; the label stays for the screen reader. */
    private fun MaterialButton.showProgress(show: Boolean) {
        if (show == (icon is IndeterminateDrawable<*>)) return
        icon = if (show) {
            val spec = CircularProgressIndicatorSpec(context, null, 0,
                com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall)
            spec.indicatorColors = intArrayOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            IndeterminateDrawable.createCircularDrawable(context, spec)
        } else null
    }

    /**
     * Google's scanner runs in Play services and needs no camera permission. Its answer may come
     * after this view is gone, so the view model is captured up front.
     */
    private fun scan() {
        val model = viewModel
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(requireContext(), options).startScan()
            .addOnSuccessListener { barcode -> model.onScanned(barcode.rawValue.orEmpty()) }
            .addOnFailureListener { model.onScannerUnavailable() }
    }

    override fun onDestroyView() {
        shownState = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "WebLoginBottomSheet"
    }
}
