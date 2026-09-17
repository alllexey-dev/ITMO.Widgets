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
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.ui.messageRes
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.databinding.DialogFriendSelectorBinding
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorScope
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorUiState
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorViewModel
import dev.alllexey.itmowidgets.feature.friendselector.presentation.PeopleResults
import dev.alllexey.itmowidgets.feature.friendselector.presentation.RecentFriendOrder
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
    private var allFriends: List<UserSummary> = emptyList()
    private var initialSelectedIsu: Int? = null
    private var pendingFriend: UserSummary? = null
    private var selectionInitialized = false
    private lateinit var recentOrder: RecentFriendOrder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selection = if (savedInstanceState?.containsKey(STATE_SELECTED_ISU) == true) {
            savedInstanceState.getInt(STATE_SELECTED_ISU)
        } else arguments?.getInt(FriendSelectionContract.ARG_SELECTED_ISU)
        initialSelectedIsu = selection?.takeIf { it != FriendSelectionContract.NO_USER_ISU }
        recentOrder = RecentFriendOrder(savedInstanceState?.getIntegerArrayList(STATE_RECENT_ORDER))
    }

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
        updateApplyButton()
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_ISU, selectedIsu() ?: FriendSelectionContract.NO_USER_ISU)
        recentOrder.snapshot?.let { outState.putIntegerArrayList(STATE_RECENT_ORDER, ArrayList(it)) }
        super.onSaveInstanceState(outState)
    }

    private fun selectedIsu(): Int? = if (selectionInitialized) pendingFriend?.isu else initialSelectedIsu

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        binding.recentRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun setupLists() {
        friendsAdapter = FriendSelectorAdapter(selectedIsu(), ::selectFriend, ::openProfile)
        recentAdapter = RecentFriendAdapter(::selectRecentItem).apply { setSelectedIsu(selectedIsu()) }

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
        binding.stateAction.setOnClickListener { retry() }
        binding.searchInput.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString().orEmpty()
            viewModel.onQueryChanged(query)
            if (currentScope() == FriendSelectorScope.FRIENDS) filterFriends(query)
        }
        binding.scopeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val scope = if (checkedId == R.id.scope_all) FriendSelectorScope.ALL else FriendSelectorScope.FRIENDS
            binding.searchLayout.hint = getString(
                if (scope == FriendSelectorScope.ALL) R.string.friend_picker_search_people_hint
                else R.string.friend_picker_search_hint
            )
            viewModel.selectScope(scope)
        }
        binding.applyButton.setOnClickListener { applySelection() }
    }

    private fun observeState() {
        viewModel.uiState
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach { state ->
                binding.scopeToggle.isVisible =
                    state is FriendSelectorUiState.Content || state is FriendSelectorUiState.Empty
                when (state) {
                    FriendSelectorUiState.Loading -> renderLoading()
                    is FriendSelectorUiState.Content -> renderContent(state)
                    FriendSelectorUiState.Empty -> renderEmpty()
                    is FriendSelectorUiState.Error -> renderError(
                        getString(state.error.messageRes()),
                        canRetry = true
                    )
                    FriendSelectorUiState.Disabled -> renderError(
                        getString(R.string.friend_picker_disabled),
                        canRetry = false
                    )
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
        // The own profile arrives after the list; the chip must not stay generic.
        viewModel.currentUser
            .flowWithLifecycle(viewLifecycleOwner.lifecycle)
            .onEach(recentAdapter::setCurrentUser)
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun renderLoading() {
        binding.progress.isVisible = true
        binding.stateContainer.isVisible = false
        binding.recyclerView.isVisible = false
        binding.applyButton.isEnabled = false
    }

    private fun renderContent(state: FriendSelectorUiState.Content) {
        binding.progress.isVisible = false
        binding.applyButton.isEnabled = true

        allFriends = state.friends
        if (!selectionInitialized) {
            pendingFriend = initialSelectedIsu?.let { isu ->
                allFriends.firstOrNull { it.isu == isu && it.sharing.schedule }
            }
            selectionInitialized = true
        }

        val recentFriends = recentOrder.resolve(state.friends, state.recentFriends, initialSelectedIsu)
        recentAdapter.submitItems(recentFriends, pendingFriend?.isu, state.currentUser)
        friendsAdapter.setSelectedIsu(pendingFriend?.isu)

        when (state.scope) {
            FriendSelectorScope.FRIENDS -> {
                binding.stateContainer.isVisible = false
                filterFriends(binding.searchInput.text?.toString().orEmpty())
            }
            FriendSelectorScope.ALL -> renderPeople(state.people)
        }
        updateApplyButton()
    }

    private fun renderPeople(people: PeopleResults) {
        when (people) {
            PeopleResults.Idle -> showState(
                icon = R.drawable.ic_search,
                title = getString(R.string.friend_picker_people_idle_title),
                description = getString(R.string.friend_picker_people_idle_description)
            )
            PeopleResults.Loading -> {
                binding.stateContainer.isVisible = false
                binding.recyclerView.isVisible = false
                binding.progress.isVisible = true
            }
            is PeopleResults.Content -> {
                friendsAdapter.submitList(people.people)
                if (people.people.isEmpty()) {
                    showState(
                        icon = R.drawable.ic_person,
                        title = getString(R.string.friend_picker_people_empty_title),
                        description = getString(R.string.friend_picker_people_empty_description)
                    )
                } else {
                    binding.stateContainer.isVisible = false
                    binding.recyclerView.isVisible = true
                }
            }
            is PeopleResults.Error -> showState(
                icon = R.drawable.ic_error_rounded,
                title = getString(R.string.common_load_error_title),
                description = getString(people.error.messageRes()),
                retry = true
            )
        }
    }

    private fun renderEmpty() {
        binding.progress.isVisible = false
        binding.applyButton.isEnabled = true
        allFriends = emptyList()
        pendingFriend = null
        selectionInitialized = true
        val recentFriends = recentOrder.resolve(emptyList(), emptyList(), initialSelectedIsu)
        recentAdapter.submitItems(recentFriends, null, currentUser())
        friendsAdapter.submitList(emptyList())
        showState(
            icon = R.drawable.ic_group,
            title = getString(R.string.friends_empty_title),
            description = getString(R.string.friends_empty_description)
        )
        updateApplyButton()
    }

    private fun renderError(message: String, canRetry: Boolean) {
        binding.progress.isVisible = false
        binding.applyButton.isEnabled = false
        showState(
            icon = if (canRetry) R.drawable.ic_error_rounded else R.drawable.ic_lock,
            title = getString(R.string.common_load_error_title),
            description = message,
            retry = canRetry
        )
    }

    private fun showState(icon: Int, title: String, description: String, retry: Boolean = false) {
        binding.recyclerView.isVisible = false
        binding.progress.isVisible = false
        binding.stateContainer.isVisible = true
        binding.stateIcon.setImageResource(icon)
        binding.stateTitle.text = title
        binding.stateDescription.text = description
        binding.stateAction.isVisible = retry
    }

    private fun retry() {
        if (currentScope() == FriendSelectorScope.ALL) viewModel.retrySearch() else viewModel.refresh()
    }

    private fun filterFriends(query: String) {
        val normalizedQuery = query.trim().lowercase()
        val filtered = if (normalizedQuery.isEmpty()) {
            allFriends
        } else {
            allFriends.filter { friend ->
                friend.name.lowercase().contains(normalizedQuery) ||
                    friend.isu.toString().contains(normalizedQuery) ||
                    friend.groups.any { group ->
                        group.name.lowercase().contains(normalizedQuery) ||
                            group.facultyShortName.lowercase().contains(normalizedQuery) ||
                            group.course.toString().contains(normalizedQuery)
                    }
            }
        }

        friendsAdapter.submitList(filtered)
        val isEmpty = filtered.isEmpty() &&
            viewModel.uiState.value is FriendSelectorUiState.Content
        if (isEmpty) {
            showState(
                icon = R.drawable.ic_search,
                title = getString(R.string.friend_picker_empty_title),
                description = getString(R.string.friend_picker_empty_description)
            )
        } else {
            binding.stateContainer.isVisible = false
            binding.recyclerView.isVisible = true
        }
    }

    private fun selectFriend(friend: UserSummary) {
        selectionInitialized = true
        pendingFriend = friend
        friendsAdapter.setSelectedIsu(friend.isu)
        updateRecentSelection()
        updateApplyButton()
    }

    private fun selectRecentItem(item: RecentFriendItem) {
        selectionInitialized = true
        pendingFriend = (item as? RecentFriendItem.Friend)?.user
        friendsAdapter.setSelectedIsu(pendingFriend?.isu)
        updateRecentSelection()
        updateApplyButton()
    }

    /** The profile is a contextual screen; the sheet has nothing to add once it opens. */
    private fun openProfile(user: UserSummary) {
        dismiss()
        openScreen(AppScreen.USER_PROFILE, bundleOf(UserScreenArgs.ISU to user.isu))
    }

    private fun updateRecentSelection() {
        recentAdapter.setSelectedIsu(pendingFriend?.isu)
    }

    private fun currentUser(): UserSummary? = viewModel.currentUser.value

    private fun currentScope(): FriendSelectorScope {
        return if (binding.scopeToggle.checkedButtonId == R.id.scope_all) {
            FriendSelectorScope.ALL
        } else {
            FriendSelectorScope.FRIENDS
        }
    }

    private fun updateApplyButton() {
        binding.applyButton.text = pendingFriend?.let { friend ->
            getString(
                R.string.friend_picker_apply_friend,
                friend.name.substringBefore(" ")
            )
        } ?: getString(R.string.friend_picker_apply_my)
    }

    private fun applySelection() {
        val friend = pendingFriend
        if (friend != null) {
            viewModel.recordSelection(friend.isu)
        }
        parentFragmentManager.setFragmentResult(
            FriendSelectionContract.RESULT_KEY,
            bundleOf(
                FriendSelectionContract.RESULT_USE_MY_SCHEDULE to (friend == null),
                FriendSelectionContract.RESULT_USER_ISU to (
                    friend?.isu ?: FriendSelectionContract.NO_USER_ISU
                ),
                FriendSelectionContract.RESULT_USER_NAME to friend?.name.orEmpty(),
                FriendSelectionContract.RESULT_USER_PICTURE_URL to friend?.pictureUrl.orEmpty()
            )
        )
        dismiss()
    }

    companion object {
        const val TAG = "FriendSelectorBottomSheet"

        private const val STATE_RECENT_ORDER = "recent_friend_order"
        private const val STATE_SELECTED_ISU = "pending_friend_isu"
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
