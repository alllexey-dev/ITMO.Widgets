package dev.alllexey.itmowidgets.feature.friendselector.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.databinding.DialogFriendSelectorBinding
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorBody
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorEvent
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorScope
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorUiState
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

@AndroidEntryPoint
class FriendSelectorDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogFriendSelectorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FriendSelectorViewModel by viewModels()

    private lateinit var friendsAdapter: FriendSelectorAdapter
    private lateinit var recentAdapter: RecentFriendAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFriendSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupLists()
        setupListeners()
        observeState()
        observeEvents()
        bindApplyButton(viewModel.uiState.value.applyTarget)
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = (dialog as? BottomSheetDialog)
            ?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * MAX_HEIGHT_RATIO).roundToInt()
        }
        BottomSheetBehavior.from(bottomSheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        binding.recentRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun setupLists() {
        val selectedIsu = viewModel.uiState.value.selectedIsu
        friendsAdapter = FriendSelectorAdapter(selectedIsu, viewModel::select, ::openProfile)
        recentAdapter = RecentFriendAdapter(::selectRecentItem).apply { setSelectedIsu(selectedIsu) }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = friendsAdapter
        }
        binding.recentRecyclerView.apply {
            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = recentAdapter
        }
    }

    private fun setupListeners() {
        binding.closeButton.setOnClickListener { dismiss() }
        binding.stateAction.setOnClickListener { viewModel.retry() }
        binding.searchInput.doOnTextChanged { text, _, _, _ ->
            viewModel.onQueryChanged(text?.toString().orEmpty())
        }
        binding.scopeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.selectScope(
                if (checkedId == R.id.scope_all) FriendSelectorScope.ALL else FriendSelectorScope.FRIENDS
            )
        }
        binding.applyButton.setOnClickListener { viewModel.apply() }
    }

    private fun observeState() {
        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun observeEvents() {
        viewModel.events
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { event ->
                when (event) {
                    is FriendSelectorEvent.Apply -> deliver(event.target)
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(state: FriendSelectorUiState) {
        binding.scopeToggle.isVisible = state.showsScope
        bindScope(state.scope)
        binding.applyButton.isEnabled = state.canApply
        bindApplyButton(state.applyTarget)
        // The own profile can arrive after the list or never; the chip must not stay generic.
        recentAdapter.setCurrentUser(state.currentUser)
        if (state.canApply) {
            recentAdapter.submitItems(state.recentFriends, state.selectedIsu, state.currentUser)
            friendsAdapter.setSelectedIsu(state.selectedIsu)
        }
        renderBody(state.body)
    }

    private fun bindScope(scope: FriendSelectorScope) {
        val buttonId = if (scope == FriendSelectorScope.ALL) R.id.scope_all else R.id.scope_friends
        if (binding.scopeToggle.checkedButtonId != buttonId) binding.scopeToggle.check(buttonId)
        binding.searchLayout.hint = getString(
            if (scope == FriendSelectorScope.ALL) R.string.friend_picker_search_people_hint
            else R.string.friend_picker_search_hint
        )
    }

    private fun renderBody(body: FriendSelectorBody) {
        when (body) {
            FriendSelectorBody.Loading, FriendSelectorBody.PeopleLoading -> showProgress()
            is FriendSelectorBody.Users -> {
                friendsAdapter.submitList(body.users)
                showList()
            }
            FriendSelectorBody.NoMatches -> {
                friendsAdapter.submitList(emptyList())
                showState(
                    icon = R.drawable.ic_search,
                    title = getString(R.string.friend_picker_empty_title),
                    description = null
                )
            }
            FriendSelectorBody.PeopleIdle -> showState(
                icon = R.drawable.ic_search,
                title = getString(R.string.friend_picker_people_idle_title),
                description = getString(R.string.friend_picker_people_idle_description)
            )
            FriendSelectorBody.PeopleEmpty -> {
                friendsAdapter.submitList(emptyList())
                showState(
                    icon = R.drawable.ic_person,
                    title = getString(R.string.friend_picker_people_empty_title),
                    description = getString(R.string.friend_picker_people_empty_description)
                )
            }
            is FriendSelectorBody.PeopleError -> showState(
                icon = R.drawable.ic_error,
                title = getString(R.string.common_load_error_title),
                description = getString(body.error.messageRes()),
                retry = true
            )
            FriendSelectorBody.NoFriends -> {
                friendsAdapter.submitList(emptyList())
                showState(
                    icon = R.drawable.ic_group,
                    title = getString(R.string.friends_empty_title),
                    description = getString(R.string.friends_empty_description)
                )
            }
            FriendSelectorBody.Disabled -> showState(
                icon = R.drawable.ic_lock,
                title = getString(R.string.common_load_error_title),
                description = getString(R.string.friend_picker_disabled)
            )
            is FriendSelectorBody.Error -> showState(
                icon = R.drawable.ic_error,
                title = getString(R.string.common_load_error_title),
                description = getString(body.error.messageRes()),
                retry = true
            )
        }
    }

    private fun showProgress() {
        binding.progress.isVisible = true
        binding.stateContainer.isVisible = false
        binding.recyclerView.isVisible = false
    }

    private fun showList() {
        binding.progress.isVisible = false
        binding.stateContainer.isVisible = false
        binding.recyclerView.isVisible = true
    }

    private fun showState(icon: Int, title: String, description: String?, retry: Boolean = false) {
        binding.recyclerView.isVisible = false
        binding.progress.isVisible = false
        binding.stateContainer.isVisible = true
        binding.stateIcon.setImageResource(icon)
        binding.stateTitle.text = title
        binding.stateDescription.isVisible = description != null
        binding.stateDescription.text = description
        binding.stateAction.isVisible = retry
    }

    private fun selectRecentItem(item: RecentFriendItem) {
        when (item) {
            RecentFriendItem.MySchedule -> viewModel.selectOwnSchedule()
            is RecentFriendItem.Friend -> viewModel.select(item.user)
        }
    }

    /** The profile is a contextual screen; the sheet has nothing to add once it opens. */
    private fun openProfile(user: UserSummary) {
        dismiss()
        openUserProfile(user.isu)
    }

    private fun bindApplyButton(target: UserSummary?) {
        binding.applyButton.text = target?.let { friend ->
            getString(
                R.string.friend_picker_apply_friend,
                friend.name.substringBefore(" ")
            )
        } ?: getString(R.string.friend_picker_apply_my)
    }

    private fun deliver(target: UserSummary?) {
        parentFragmentManager.setFragmentResult(
            FriendSelectionContract.RESULT_KEY,
            bundleOf(
                FriendSelectionContract.RESULT_USE_MY_SCHEDULE to (target == null),
                FriendSelectionContract.RESULT_USER_ISU to (
                    target?.isu ?: FriendSelectionContract.NO_USER_ISU
                ),
                FriendSelectionContract.RESULT_USER_NAME to target?.name.orEmpty(),
                FriendSelectionContract.RESULT_USER_PICTURE_URL to target?.pictureUrl.orEmpty()
            )
        )
        dismiss()
    }

    companion object {
        const val TAG = "FriendSelectorBottomSheet"

        private const val MAX_HEIGHT_RATIO = 0.9f

        fun newInstance(selectedIsu: Int? = null) = FriendSelectorDialogFragment().apply {
            arguments = bundleOf(
                FriendSelectionContract.ARG_SELECTED_ISU to (
                    selectedIsu ?: FriendSelectionContract.NO_USER_ISU
                )
            )
        }

        fun show(manager: FragmentManager, selectedIsu: Int? = null) {
            newInstance(selectedIsu).show(manager, TAG)
        }
    }
}
