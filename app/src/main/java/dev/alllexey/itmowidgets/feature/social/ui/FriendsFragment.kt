package dev.alllexey.itmowidgets.feature.social.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.ui.applyAppRefreshColors
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.databinding.FragmentFriendsBinding
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsEmpty
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsEvent
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsTab
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsUiState
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsViewModel
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class FriendsFragment : Fragment() {

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FriendsViewModel by viewModels()

    private lateinit var adapter: UserListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = UserListAdapter(
            onAction = viewModel::onAction,
            onOpen = ::openProfile
        )
        binding.recyclerView.adapter = adapter
        binding.swipeRefreshLayout.applyAppRefreshColors()
        binding.swipeRefreshLayout.setOnRefreshListener({ viewModel.refresh(RefreshMode.Pull) })
        binding.backButton.setOnClickListener { closeScreen() }
        binding.searchButton.setOnClickListener { openScreen(AppScreen.USER_SEARCH) }
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.selectTab(if (tab.position == 0) FriendsTab.FRIENDS else FriendsTab.REQUESTS)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::handle)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: FriendsUiState) = with(binding) {
        // A list already on screen stays while the repository reloads; only an empty screen shows the placeholder.
        val keepsList = state is FriendsUiState.Loading && adapter.itemCount > 0
        loading.isVisible = state is FriendsUiState.Loading && !keepsList
        swipeRefreshLayout.isRefreshing = (state as? FriendsUiState.Content)?.refreshing == true || keepsList
        tabs.isVisible = state is FriendsUiState.Content || keepsList
        when (state) {
            FriendsUiState.Loading -> {
                swipeRefreshLayout.isVisible = keepsList
                stateContainer.isVisible = false
            }
            FriendsUiState.Disabled -> showState(
                icon = R.drawable.ic_lock,
                title = getString(R.string.friends_disabled_title),
                description = getString(R.string.friends_disabled_description),
                action = getString(R.string.settings_title)
            ) { openScreen(AppScreen.SETTINGS) }
            is FriendsUiState.Error -> showState(
                icon = R.drawable.ic_error,
                title = getString(R.string.common_load_error_title),
                description = getString(state.error.messageRes()),
                action = getString(R.string.common_retry)
            ) { viewModel.refresh(RefreshMode.Force) }
            is FriendsUiState.Content -> renderContent(state)
        }
    }

    private fun renderContent(state: FriendsUiState.Content) = with(binding) {
        val requestsTab = tabs.getTabAt(1)
        if (requestsTab != null) {
            if (state.incomingCount > 0) {
                requestsTab.orCreateBadge.apply {
                    number = state.incomingCount
                    // The default offset pulls the badge over the last letter of «Заявки».
                    horizontalOffsetWithText = 0
                }
            } else {
                requestsTab.removeBadge()
            }
        }
        val selected = if (state.tab == FriendsTab.FRIENDS) 0 else 1
        if (tabs.selectedTabPosition != selected) tabs.getTabAt(selected)?.select()

        adapter.submitList(state.items)
        val empty = state.empty
        if (empty != null) {
            val friends = empty == FriendsEmpty.NO_FRIENDS
            showState(
                icon = if (friends) R.drawable.ic_group else R.drawable.ic_how_to_reg,
                title = getString(if (friends) R.string.friends_empty_title else R.string.friends_requests_empty_title),
                description = getString(R.string.friends_empty_description).takeIf { friends },
                action = getString(R.string.friends_find_people).takeIf { friends }
            ) { openScreen(AppScreen.USER_SEARCH) }
        } else {
            stateContainer.isVisible = false
            swipeRefreshLayout.isVisible = true
        }
    }

    private fun showState(
        icon: Int,
        title: String,
        description: String?,
        action: String?,
        onAction: () -> Unit
    ) = with(binding) {
        swipeRefreshLayout.isVisible = false
        stateContainer.isVisible = true
        stateIcon.setImageResource(icon)
        stateTitle.text = title
        stateDescription.isVisible = description != null
        stateDescription.text = description
        stateAction.isVisible = action != null
        stateAction.text = action
        stateAction.setOnClickListener { onAction() }
    }

    private fun handle(event: FriendsEvent) {
        when (event) {
            is FriendsEvent.ActionFailed -> Snackbar.make(
                binding.root,
                getString(R.string.friends_action_failed, getString(event.error.messageRes())),
                Snackbar.LENGTH_LONG
            ).show()
            is FriendsEvent.ConfirmRemove -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.friends_remove_confirm_title)
                .setMessage(getString(R.string.friends_remove_confirm_message, event.name.resolve(requireContext())))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.user_action_remove) { _, _ -> viewModel.removeFriend(event.isu) }
                .show()
        }
    }

    private fun openProfile(row: UserRowUi) {
        openUserProfile(row.isu)
    }
}
