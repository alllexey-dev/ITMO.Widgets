package dev.alllexey.itmowidgets.feature.auth.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.DialogRefreshTokenLoginBinding
import dev.alllexey.itmowidgets.databinding.FragmentAuthBinding
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthUiState
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthViewModel
import dev.alllexey.itmowidgets.core.ui.resolve
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels()

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.clearError()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ImageViewCompat.setImageTintList(binding.authLogo, null)
        binding.itmoIdLoginButton.setOnClickListener {
            viewModel.clearError()
            loginLauncher.launch(Intent(requireContext(), LoginActivity::class.java))
        }
        binding.refreshTokenLoginButton.setOnClickListener {
            showRefreshTokenDialog()
        }

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showRefreshTokenDialog() {
        val dialogBinding = DialogRefreshTokenLoginBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.auth_manual_dialog_title)
            .setMessage(R.string.auth_manual_dialog_description)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.auth_sign_in, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val token = dialogBinding.refreshTokenInput.text?.toString().orEmpty()
                if (token.isBlank()) {
                    dialogBinding.refreshTokenLayout.error =
                        getString(R.string.auth_manual_token_required)
                    return@setOnClickListener
                }
                dialogBinding.refreshTokenInput.text?.clear()
                dialog.dismiss()
                viewModel.signInWithRefreshToken(token)
            }
        }
        dialog.setOnDismissListener {
            dialogBinding.refreshTokenInput.text?.clear()
        }
        dialog.show()
    }

    private fun render(state: AuthUiState) {
        val loginEnabled = !state.manualLoginInProgress &&
            !state.sessionTransitionInProgress
        binding.authProgress.isVisible = state.initializing
        binding.authContent.isVisible = !state.initializing
        binding.authDescription.setText(
            if (state.reauthenticationRequired) {
                R.string.auth_reauthentication_description
            } else {
                R.string.auth_description
            }
        )
        binding.itmoIdLoginButton.isEnabled = loginEnabled
        binding.refreshTokenLoginButton.isEnabled = loginEnabled
        binding.manualLoginProgress.isVisible = state.manualLoginInProgress ||
            state.sessionTransitionInProgress
        binding.authError.isVisible = state.error != null
        binding.authError.text = state.error?.resolve(requireContext())
    }
}
