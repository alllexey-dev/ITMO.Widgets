package dev.alllexey.itmowidgets.feature.social.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.applyAppRefreshColors
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.databinding.FragmentUserFriendsBinding
import dev.alllexey.itmowidgets.feature.social.presentation.UserFriendsViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class UserFriendsFragment : Fragment() {
    private var _binding: FragmentUserFriendsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: UserFriendsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.title.text = if (viewModel.name.isBlank()) getString(R.string.friends_title)
            else getString(R.string.user_friends_owner, viewModel.name)
        binding.backButton.setOnClickListener { closeScreen() }
        val adapter = UserListAdapter(onAction = { _, _ -> }, onOpen = { UserProfileNavigation.open(this, it.isu) })
        binding.recyclerView.adapter = adapter
        binding.swipeRefreshLayout.applyAppRefreshColors()
        binding.swipeRefreshLayout.setOnRefreshListener(viewModel::load)
        val renderer = UserFriendsRenderer(binding, adapter, viewModel::load) { openScreen(AppScreen.SETTINGS) }
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(renderer::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.refreshErrors.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { error ->
            Snackbar.make(binding.root, error.messageRes(), Snackbar.LENGTH_LONG)
                .setAction(R.string.common_retry) { viewModel.load() }.show()
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
