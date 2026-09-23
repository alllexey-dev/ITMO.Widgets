package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.databinding.FragmentUserSearchBinding
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchEvent
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class UserSearchFragment : Fragment() {

    private var _binding: FragmentUserSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UserSearchViewModel by viewModels()

    private lateinit var adapter: UserListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = UserListAdapter(
            onAction = viewModel::onAction,
            onOpen = ::openProfile,
            onLoadMore = viewModel::loadMore
        )
        binding.recyclerView.adapter = adapter
        binding.backButton.setOnClickListener { closeScreen() }
        binding.searchInput.doOnTextChanged { text, _, _, _ ->
            viewModel.onQueryChanged(text?.toString().orEmpty())
        }
        if (savedInstanceState == null) binding.searchInput.requestFocus()

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.eventFlow
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::handle)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: UserSearchUiState) = with(binding) {
        loading.isVisible = state is UserSearchUiState.Loading
        loadingMore.isVisible = (state as? UserSearchUiState.Content)?.loadingMore == true
        when (state) {
            UserSearchUiState.Idle -> showState(
                icon = R.drawable.ic_search,
                title = getString(R.string.user_search_idle_title),
                description = getString(R.string.user_search_idle_description),
                action = null
            )
            UserSearchUiState.Loading -> {
                recyclerView.isVisible = false
                stateContainer.isVisible = false
            }
            UserSearchUiState.Empty -> showState(
                icon = R.drawable.ic_person,
                title = getString(R.string.user_search_empty_title),
                description = null,
                action = null
            )
            is UserSearchUiState.Error -> showState(
                icon = R.drawable.ic_error_rounded,
                title = getString(R.string.common_load_error_title),
                description = getString(state.error.messageRes()),
                action = getString(R.string.common_retry)
            ) { viewModel.retry() }
            is UserSearchUiState.Content -> {
                adapter.submitList(state.items)
                stateContainer.isVisible = false
                recyclerView.isVisible = true
            }
        }
    }

    private fun showState(
        icon: Int,
        title: String,
        description: String?,
        action: String?,
        onAction: () -> Unit = {}
    ) = with(binding) {
        recyclerView.isVisible = false
        stateContainer.isVisible = true
        stateIcon.setImageResource(icon)
        stateTitle.text = title
        stateDescription.isVisible = description != null
        stateDescription.text = description
        stateAction.isVisible = action != null
        stateAction.text = action
        stateAction.setOnClickListener { onAction() }
    }

    private fun handle(event: UserSearchEvent) {
        when (event) {
            is UserSearchEvent.ActionFailed -> Snackbar.make(
                binding.root,
                getString(R.string.friends_action_failed, getString(event.error.messageRes())),
                Snackbar.LENGTH_LONG
            ).show()
            is UserSearchEvent.Invite -> {
                val text = getString(R.string.user_search_invite_text, getString(R.string.latest_release_url))
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(intent, getString(R.string.user_search_invite_chooser)))
            }
        }
    }

    private fun openProfile(row: UserRowUi) {
        UserProfileNavigation.open(this, row.isu)
    }
}
