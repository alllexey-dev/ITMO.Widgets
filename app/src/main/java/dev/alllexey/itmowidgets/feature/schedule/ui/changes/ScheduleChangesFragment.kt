package dev.alllexey.itmowidgets.feature.schedule.ui.changes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.databinding.FragmentScheduleChangesBinding
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.androidx.viewmodel.ext.android.viewModel

/** The schedule changes of the last 30 days by the day they were found. Opening it marks everything read. */
@AndroidEntryPoint
class ScheduleChangesFragment : Fragment() {
    private var _binding: FragmentScheduleChangesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScheduleChangesViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScheduleChangesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backButton.setOnClickListener { closeScreen() }
        val adapter = ScheduleChangesAdapter()
        binding.recyclerView.adapter = adapter
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { state -> render(adapter, state) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onStart() {
        super.onStart()
        viewModel.setVisible(true)
    }

    override fun onStop() {
        viewModel.setVisible(false)
        super.onStop()
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    /** The local file answers at once, so loading is a blank area; list and empty state switch after the diff lands. */
    private fun render(adapter: ScheduleChangesAdapter, state: ScheduleChangesUiState) {
        val items = (state as? ScheduleChangesUiState.Content)?.days?.toItems().orEmpty()
        adapter.submitList(items) {
            val binding = _binding ?: return@submitList
            binding.recyclerView.isVisible = state is ScheduleChangesUiState.Content
            binding.stateContainer.isVisible = state is ScheduleChangesUiState.Empty
        }
    }
}
