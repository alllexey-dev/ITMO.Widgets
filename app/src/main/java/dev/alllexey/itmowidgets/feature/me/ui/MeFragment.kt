package dev.alllexey.itmowidgets.feature.me.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.FragmentMeBinding
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class MeFragment : Fragment() {

    private var _binding: FragmentMeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.debugToolsRow.isVisible = BuildConfig.DEBUG
        binding.debugDivider.isVisible = BuildConfig.DEBUG

        binding.settingsRow.setOnClickListener {
            findNavController().navigate(R.id.action_me_to_settings)
        }
        binding.debugToolsRow.setOnClickListener {
            findNavController().navigate(R.id.action_me_to_debug_tools)
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

    private fun render(state: MeUiState) {
        val user = state.user
        binding.profileAvatar.setUser(user?.name, user?.pictureUrl)
        binding.profileName.text = user?.name ?: getString(R.string.me_unknown_user)
        binding.profileMeta.isVisible = user?.isu != null
        binding.profileMeta.text = user?.isu?.let { getString(R.string.me_isu, it) }
    }
}
