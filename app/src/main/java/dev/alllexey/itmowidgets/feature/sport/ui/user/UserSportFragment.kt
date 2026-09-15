package dev.alllexey.itmowidgets.feature.sport.ui.user

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.applyAppRefreshColors
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.closeScreen
import dev.alllexey.itmowidgets.databinding.FragmentUserSportBinding
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.user.UserSportViewModel
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingAdapter
import dev.alllexey.itmowidgets.feature.sport.ui.my.SportBookingListener
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Another user's confirmed and pending sport lessons, read-only. */
@AndroidEntryPoint
class UserSportFragment : Fragment(), SportBookingListener {

    private var _binding: FragmentUserSportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UserSportViewModel by viewModels()

    @Inject
    lateinit var timeProvider: AcademicTimeProvider

    private lateinit var adapter: SportBookingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserSportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.title.text = if (viewModel.name.isBlank()) {
            getString(R.string.user_profile_sport_title)
        } else {
            getString(R.string.user_sport_title, viewModel.name.substringBefore(" "))
        }
        binding.backButton.setOnClickListener { closeScreen() }
        adapter = SportBookingAdapter(timeProvider, this, readOnly = true)
        binding.recyclerView.adapter = adapter
        binding.swipeRefreshLayout.applyAppRefreshColors()
        binding.swipeRefreshLayout.setOnRefreshListener(viewModel::load)
        binding.stateAction.setOnClickListener { viewModel.load() }

        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: UserSportUiState) = with(binding) {
        loading.isVisible = state is UserSportUiState.Loading
        swipeRefreshLayout.isRefreshing = (state as? UserSportUiState.Content)?.refreshing == true
        when (state) {
            UserSportUiState.Loading -> {
                swipeRefreshLayout.isVisible = false
                stateContainer.isVisible = false
            }
            is UserSportUiState.Error -> {
                val hidden = state.error == AppError.Forbidden
                showState(
                    icon = if (hidden) R.drawable.ic_lock else R.drawable.ic_error_rounded,
                    title = getString(if (hidden) R.string.user_sport_hidden_title else R.string.common_load_error_title),
                    description = getString(if (hidden) R.string.user_profile_hidden else state.error.messageRes()),
                    retry = !hidden
                )
            }
            is UserSportUiState.Content -> {
                adapter.submitList(state.bookings)
                if (state.bookings.isEmpty()) {
                    showState(
                        icon = R.drawable.ic_exercise,
                        title = getString(R.string.user_sport_empty_title),
                        description = getString(R.string.user_sport_empty_description),
                        retry = false
                    )
                } else {
                    stateContainer.isVisible = false
                    swipeRefreshLayout.isVisible = true
                }
            }
        }
    }

    private fun showState(icon: Int, title: String, description: String, retry: Boolean) = with(binding) {
        swipeRefreshLayout.isVisible = false
        stateContainer.isVisible = true
        stateIcon.setImageResource(icon)
        stateTitle.text = title
        stateDescription.text = description
        stateAction.isVisible = retry
    }

    override fun onUnSign(booking: SportBooking) = Unit

    override fun onBookingClick(booking: SportBooking) = Unit

    override fun onLocationClick(booking: SportBooking) {
        val address = booking.extractBuildingAddress() ?: return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))))
    }
}
