package dev.alllexey.itmowidgets.feature.recordbook.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import kotlinx.coroutines.flow.combine
import dev.alllexey.itmowidgets.feature.recordbook.presentation.hasScheduleContent
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTab
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.databinding.FragmentRecordbookSubjectBinding
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectUiState
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class RecordbookSubjectFragment : Fragment() {
    private var _binding: FragmentRecordbookSubjectBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecordbookSubjectViewModel by viewModels()
    private lateinit var pages: SubjectPagerAdapter
    private val barsLogin = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.refresh()
    }
    private var lastRefreshError: AppError? = null
    private var lastBarsError: AppError? = null
    private var errorSnackbar: Snackbar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordbookSubjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        pages = SubjectPagerAdapter(this)
        binding.pager.adapter = pages
        binding.backButton.setOnClickListener { closeScreen() }
        binding.sourceButton.setOnClickListener { showRecordbookSourceInfo(requireContext()) }
        binding.stateAction.setOnClickListener { viewModel.refresh() }
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.setText(if (pages.tabAt(position) == SubjectTab.SCORES) R.string.subject_tab_scores else R.string.subject_tab_schedule)
        }.attach()
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.selectTab(pages.tabAt(position))
            }
        })
        combine(viewModel.uiState, viewModel.tab) { state, tab -> state to tab }
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { (state, tab) -> render(state, tab) }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        binding.pager.adapter = null
        errorSnackbar?.dismiss()
        errorSnackbar = null
        lastRefreshError = null
        lastBarsError = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: RecordbookSubjectUiState, tab: SubjectTab) {
        if (state !is RecordbookSubjectUiState.Content) binding.loading.isVisible = state is RecordbookSubjectUiState.Loading
        val refreshError = (state as? RecordbookSubjectUiState.Content)?.refreshError
        val barsError = (state as? RecordbookSubjectUiState.Content)?.barsError
        if (refreshError != lastRefreshError || barsError != lastBarsError) {
            errorSnackbar?.dismiss()
            errorSnackbar = refreshError?.let {
                Snackbar.make(binding.root, getString(R.string.recordbook_refresh_error, getString(it.messageRes())), Snackbar.LENGTH_LONG)
                    .setAction(R.string.common_retry) { viewModel.refresh() }.also(Snackbar::show)
            } ?: barsError?.let { error ->
                recordbookBarsSnackbar(binding.root, error, { viewModel.refresh() }) {
                    barsLogin.launch(Intent(requireContext(), BarsLoginActivity::class.java))
                }
            }
            lastRefreshError = refreshError
            lastBarsError = barsError
        }
        when (state) {
            RecordbookSubjectUiState.Loading -> {
                // The tab strip is deterministic for the period; only the pages wait for the subject.
                val tabs = tabsFor(viewModel.scheduleTabExpected)
                pages.submit(tabs)
                binding.tabs.isVisible = tabs.size > 1
                binding.pager.isVisible = false
                binding.stateContainer.isVisible = false
            }
            is RecordbookSubjectUiState.Content -> {
                // The schedule tab exists once the hub has anything; the chosen tab is kept across reloads.
                val tabs = tabsFor(state.hub.hasScheduleContent)
                pages.submit(tabs)
                binding.tabs.isVisible = tabs.size > 1
                val position = tabs.indexOf(tab).coerceAtLeast(0)
                if (binding.pager.currentItem != position) binding.pager.setCurrentItem(position, false)
                binding.loading.isVisible = false
                binding.stateContainer.isVisible = false
                binding.pager.isVisible = true
            }
            is RecordbookSubjectUiState.Error -> {
                binding.pager.isVisible = false
                binding.tabs.isVisible = false
                binding.stateContainer.isVisible = true
                binding.stateIcon.setImageResource(R.drawable.ic_error_rounded)
                binding.stateTitle.setText(R.string.common_load_error_title)
                binding.stateDescription.setText(state.error.messageRes())
                binding.stateAction.isVisible = true
            }
        }
    }

    private fun tabsFor(withSchedule: Boolean) =
        if (withSchedule) listOf(SubjectTab.SCORES, SubjectTab.SCHEDULE) else listOf(SubjectTab.SCORES)

    /** Pages are the tabs; ids are stable per tab so a reload does not recreate the current page. */
    private class SubjectPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        private var tabs: List<SubjectTab> = listOf(SubjectTab.SCORES)

        fun tabAt(position: Int): SubjectTab = tabs[position]

        fun submit(next: List<SubjectTab>) {
            if (next == tabs) return
            tabs = next
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = tabs.size
        override fun getItemId(position: Int): Long = tabs[position].ordinal.toLong()
        override fun containsItem(itemId: Long): Boolean = tabs.any { it.ordinal.toLong() == itemId }
        override fun createFragment(position: Int): Fragment = RecordbookSubjectPageFragment.newInstance(tabs[position])
    }
}
