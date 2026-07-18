package dev.alllexey.itmowidgets.feature.friendselector

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.databinding.DialogFriendSelectorBinding
import kotlin.math.roundToInt

@AndroidEntryPoint
class FriendSelectorDialogFragment : DialogFragment() {

    // region Binding

    private var _binding: DialogFriendSelectorBinding? = null
    private val binding get() = _binding!!

    // endregion

    // region ViewModel

    private val viewModel: FriendSelectorViewModel by viewModels()

    // endregion

    // region State

    private lateinit var adapter: FriendSelectorAdapter
    private var allFriends: List<UserData> = emptyList()
    private var selectedIsu: Int? = null

    // endregion

    // region Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedIsu = arguments?.getInt(ARG_SELECTED_ISU)?.takeIf { it != -1 }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFriendSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        configureWindow()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecycler()
        setupListeners()
        observeState()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // endregion

    // region Setup

    private fun configureWindow() {

        dialog?.window?.let { window ->

            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setGravity(Gravity.END)

            val metrics = resources.displayMetrics

            val targetWidth = (metrics.widthPixels * 0.92f)
                .roundToInt()
                .coerceAtMost((420 * metrics.density).roundToInt())

            window.setLayout(
                targetWidth,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun setupRecycler() {

        adapter = FriendSelectorAdapter(
            selectedIsu = selectedIsu,
            onClick = ::onFriendClicked
        )

        binding.recyclerView.layoutManager =
            LinearLayoutManager(requireContext())

        binding.recyclerView.adapter = adapter
    }

    private fun setupListeners() {

        binding.closeButton.setOnClickListener {
            dismiss()
        }

        binding.retryButton.setOnClickListener {
            viewModel.refresh()
        }

        binding.searchInput.doOnTextChanged { text, _, _, _ ->
            filterFriends(text?.toString().orEmpty())
        }
    }

    // endregion

    // region Observers

    private fun observeState() {

        viewModel.uiState.observe(viewLifecycleOwner) { state ->

            when (state) {
                FriendSelectorUiState.Loading -> renderLoading()

                is FriendSelectorUiState.Success -> renderSuccess(state)

                is FriendSelectorUiState.Error -> renderError(state.message)

                is FriendSelectorUiState.Disabled -> renderError("Кастомные сервисы выключены!")
            }
        }
    }

    // endregion

    // region Rendering

    private fun renderLoading() {
        binding.progress.isVisible = true
        binding.errorGroup.isVisible = false
        binding.emptyGroup.isVisible = false
        binding.recyclerView.isVisible = false
    }

    private fun renderSuccess(state: FriendSelectorUiState.Success) {
        binding.progress.isVisible = false
        binding.errorGroup.isVisible = false
        binding.recyclerView.isVisible = true

        allFriends = state.friends

        viewModel.preloadAvatars(requireContext(), state.friends)

        filterFriends(binding.searchInput.text?.toString().orEmpty())
    }

    private fun renderError(message: String) {
        binding.progress.isVisible = false
        binding.recyclerView.isVisible = false
        binding.emptyGroup.isVisible = false

        binding.errorGroup.isVisible = true
        binding.errorText.text = message
    }

    private fun filterFriends(query: String) {

        val q = query.trim().lowercase()

        val filtered = if (q.isEmpty()) {
            allFriends
        } else {
            allFriends.filter { friend ->
                friend.name.lowercase().contains(q) ||
                        friend.isu.toString().contains(q) ||
                        friend.groups.any { group ->
                            group.name.lowercase().contains(q) ||
                                    group.facultyShortName.lowercase().contains(q) ||
                                    group.course.toString().contains(q)
                        }
            }
        }

        adapter.submitList(filtered)

        binding.emptyGroup.isVisible =
            filtered.isEmpty() && viewModel.uiState.value is FriendSelectorUiState.Success

        binding.recyclerView.isVisible = filtered.isNotEmpty()
    }

    // endregion

    // region Actions

    private fun onFriendClicked(friend: UserData) {

        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(
                RESULT_USER_ISU to friend.isu,
                RESULT_USER_NAME to friend.name,
                RESULT_USER_PICTURE_URL to (friend.pictureUrl ?: "")
            )
        )

        dismiss()
    }

    // endregion

    companion object {

        const val TAG = "FriendSelectorDialog"

        const val RESULT_KEY = "friend_selector_result"
        const val RESULT_USER_ISU = "result_user_isu"
        const val RESULT_USER_NAME = "result_user_name"
        const val RESULT_USER_PICTURE_URL = "result_user_picture_url"

        private const val ARG_SELECTED_ISU = "arg_selected_isu"

        fun newInstance(selectedIsu: Int? = null): FriendSelectorDialogFragment {
            return FriendSelectorDialogFragment().apply {
                arguments = bundleOf(
                    ARG_SELECTED_ISU to (selectedIsu ?: -1)
                )
            }
        }

        fun show(manager: FragmentManager, selectedIsu: Int? = null) {
            newInstance(selectedIsu).show(manager, TAG)
        }
    }
}
