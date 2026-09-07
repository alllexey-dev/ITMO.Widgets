package dev.alllexey.itmowidgets.feature.me.ui

import androidx.core.view.isVisible
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.databinding.FragmentMeBinding
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState

object MeRenderer {
    fun render(binding: FragmentMeBinding, state: MeUiState) {
        val context = binding.root.context
        val user = state.user
        binding.profileAvatar.setUser(user?.name, user?.pictureUrl)
        binding.profileName.text = user?.name ?: context.getString(R.string.me_unknown_user)
        binding.profileMeta.isVisible = user?.isu != null
        binding.profileMeta.text = user?.isu?.let { context.getString(R.string.me_isu, it) }
        binding.signOutRow.isEnabled = !state.signOutInProgress
    }
}
