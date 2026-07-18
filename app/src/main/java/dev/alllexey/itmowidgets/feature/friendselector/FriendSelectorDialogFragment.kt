package dev.alllexey.itmowidgets.feature.friendselector

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.databinding.DialogFriendSelectorBinding
import kotlin.math.roundToInt

@AndroidEntryPoint
class FriendSelectorDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogFriendSelectorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FriendSelectorViewModel by viewModels()

    private lateinit var friendsAdapter: FriendSelectorAdapter
    private lateinit var recentAdapter: RecentFriendAdapter
    private var allFriends: List<UserData> = emptyList()
    private var initialSelectedIsu: Int? = null
    private var pendingFriend: UserData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialSelectedIsu = arguments?.getInt(ARG_SELECTED_ISU)?.takeIf { it != NO_USER_ISU }
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

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        binding.recentRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun setupLists() {
        friendsAdapter = FriendSelectorAdapter(initialSelectedIsu, ::selectFriend)
        recentAdapter = RecentFriendAdapter(::selectRecentItem)

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
        binding.retryButton.setOnClickListener { viewModel.refresh() }
        binding.searchInput.doOnTextChanged { text, _, _, _ ->
            filterFriends(text?.toString().orEmpty())
        }
        binding.applyButton.setOnClickListener { applySelection() }
    }

    private fun observeState() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                FriendSelectorUiState.Loading -> renderLoading()
                is FriendSelectorUiState.Success -> renderSuccess(state)
                is FriendSelectorUiState.Error -> renderError(state.message, canRetry = true)
                FriendSelectorUiState.Disabled -> renderError(
                    getString(R.string.friend_picker_disabled),
                    canRetry = false
                )
            }
        }
    }

    private fun renderLoading() {
        binding.progress.isVisible = true
        binding.errorGroup.isVisible = false
        binding.emptyGroup.isVisible = false
        binding.recyclerView.isVisible = false
        binding.applyButton.isEnabled = false
    }

    private fun renderSuccess(state: FriendSelectorUiState.Success) {
        binding.progress.isVisible = false
        binding.errorGroup.isVisible = false
        binding.applyButton.isEnabled = true

        allFriends = state.friends
        pendingFriend = initialSelectedIsu?.let { isu ->
            allFriends.firstOrNull { it.isu == isu && it.settings.scheduleSharing }
        }

        val recentFriends = (listOfNotNull(pendingFriend) + state.recentFriends)
            .filter { it.settings.scheduleSharing }
            .distinctBy(UserData::isu)
            .take(MAX_RECENT_FRIENDS)
        recentAdapter.submitItems(recentFriends, pendingFriend?.isu)
        friendsAdapter.setSelectedIsu(pendingFriend?.isu)
        filterFriends(binding.searchInput.text?.toString().orEmpty())
        updateApplyButton()
    }

    private fun renderError(message: String, canRetry: Boolean) {
        binding.progress.isVisible = false
        binding.recyclerView.isVisible = false
        binding.emptyGroup.isVisible = false
        binding.errorGroup.isVisible = true
        binding.errorText.text = message
        binding.retryButton.isVisible = canRetry
        binding.applyButton.isEnabled = false
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
            viewModel.uiState.value is FriendSelectorUiState.Success
        binding.emptyGroup.isVisible = isEmpty
        binding.recyclerView.isVisible = !isEmpty
    }

    private fun selectFriend(friend: UserData) {
        pendingFriend = friend
        friendsAdapter.setSelectedIsu(friend.isu)
        updateRecentSelection()
        updateApplyButton()
    }

    private fun selectRecentItem(item: RecentFriendItem) {
        pendingFriend = (item as? RecentFriendItem.Friend)?.user
        friendsAdapter.setSelectedIsu(pendingFriend?.isu)
        updateRecentSelection()
        updateApplyButton()
    }

    private fun updateRecentSelection() {
        val state = viewModel.uiState.value as? FriendSelectorUiState.Success ?: return
        val items = (listOfNotNull(pendingFriend) + state.recentFriends)
            .filter { it.settings.scheduleSharing }
            .distinctBy(UserData::isu)
            .take(MAX_RECENT_FRIENDS)
        recentAdapter.submitItems(items, pendingFriend?.isu)
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
            RESULT_KEY,
            bundleOf(
                RESULT_USE_MY_SCHEDULE to (friend == null),
                RESULT_USER_ISU to (friend?.isu ?: NO_USER_ISU),
                RESULT_USER_NAME to friend?.name.orEmpty(),
                RESULT_USER_PICTURE_URL to friend?.pictureUrl.orEmpty()
            )
        )
        dismiss()
    }

    companion object {
        const val TAG = "FriendSelectorBottomSheet"
        const val RESULT_KEY = "friend_selector_result"
        const val RESULT_USE_MY_SCHEDULE = "result_use_my_schedule"
        const val RESULT_USER_ISU = "result_user_isu"
        const val RESULT_USER_NAME = "result_user_name"
        const val RESULT_USER_PICTURE_URL = "result_user_picture_url"

        private const val ARG_SELECTED_ISU = "arg_selected_isu"
        private const val NO_USER_ISU = -1
        private const val MAX_RECENT_FRIENDS = 5
        private const val MAX_HEIGHT_RATIO = 0.9f

        fun newInstance(selectedIsu: Int? = null) = FriendSelectorDialogFragment().apply {
            arguments = bundleOf(ARG_SELECTED_ISU to (selectedIsu ?: NO_USER_ISU))
        }

        fun show(manager: FragmentManager, selectedIsu: Int? = null) {
            newInstance(selectedIsu).show(manager, TAG)
        }
    }
}
