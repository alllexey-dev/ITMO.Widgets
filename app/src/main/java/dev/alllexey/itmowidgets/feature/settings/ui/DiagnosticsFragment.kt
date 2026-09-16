package dev.alllexey.itmowidgets.feature.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.databinding.FragmentDiagnosticsBinding
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class DiagnosticsFragment : Fragment() {

    private var _binding: FragmentDiagnosticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiagnosticsViewModel by viewModels()

    private lateinit var adapter: DiagnosticsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiagnosticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = DiagnosticsAdapter(viewModel::formatTime)
        binding.recyclerView.adapter = adapter
        binding.backButton.setOnClickListener { closeScreen() }
        binding.copyButton.setOnClickListener { copyJournal() }
        binding.clearButton.setOnClickListener { confirmClear() }

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: DiagnosticsUiState) = with(binding) {
        val entries = (state as? DiagnosticsUiState.Content)?.entries
        loading.isVisible = entries == null
        val empty = entries != null && entries.isEmpty()
        stateContainer.isVisible = empty
        recyclerView.isVisible = entries != null && entries.isNotEmpty()
        copyButton.isEnabled = entries?.isNotEmpty() == true
        clearButton.isEnabled = entries?.isNotEmpty() == true
        adapter.submitList(entries.orEmpty())
    }

    private fun copyJournal() {
        val clipboard = requireContext().getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.diagnostics_title), viewModel.exportText()))
        // Android 13+ shows its own confirmation overlay.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Snackbar.make(binding.root, R.string.diagnostics_copied, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.diagnostics_clear_confirm_title)
            .setMessage(R.string.diagnostics_clear_confirm_message)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.diagnostics_clear) { _, _ -> viewModel.clear() }
            .show()
    }
}
