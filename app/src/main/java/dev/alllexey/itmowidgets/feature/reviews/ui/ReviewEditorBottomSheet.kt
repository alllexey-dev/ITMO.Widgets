package dev.alllexey.itmowidgets.feature.reviews.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewLimits
import dev.alllexey.itmowidgets.core.ui.expandToContent
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.ui.shortPersonName
import dev.alllexey.itmowidgets.databinding.SheetReviewEditorBinding
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorEvent
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorUiState
import dev.alllexey.itmowidgets.feature.reviews.presentation.ReviewEditorViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Writes or edits the viewer's review: subject with suggestions, text, anonymity. Changes are never lost silently. */
@AndroidEntryPoint
class ReviewEditorBottomSheet : BottomSheetDialogFragment() {
    private var _binding: SheetReviewEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReviewEditorViewModel by viewModels()
    /** Programmatic updates of the fields must not read as the user's input. */
    private var rendering = false
    private var shownSuggestions: List<String>? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = close()
            })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetReviewEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        @Suppress("DEPRECATION")
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        expandToContent()
        dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            BottomSheetBehavior.from(it).isDraggable = false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        // Long subjects wrap instead of scrolling away, and the keyboard still moves on to the text.
        subject.setHorizontallyScrolling(false)
        subject.maxLines = MAX_SUBJECT_LINES
        subject.doAfterTextChanged { if (!rendering) viewModel.onSubjectChanged(it?.toString().orEmpty()) }
        text.doAfterTextChanged { if (!rendering) viewModel.onTextChanged(it?.toString().orEmpty()) }
        anonymous.setOnCheckedChangeListener { _, checked -> if (!rendering) viewModel.onAnonymousChanged(checked) }
        close.setOnClickListener { close() }
        saveButton.setOnClickListener { viewModel.save() }
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { event ->
            when (event) {
                ReviewEditorEvent.Saved -> dismiss()
                is ReviewEditorEvent.Failed -> Snackbar.make(root, event.text.resolve(requireContext()), Snackbar.LENGTH_LONG).show()
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun render(state: ReviewEditorUiState) = with(binding) {
        rendering = true
        try {
            title.text = if (state.editing) getString(R.string.review_editor_edit)
            else getString(R.string.review_editor_new, shortPersonName(viewModel.teacherName))
            if (subject.text?.toString() != state.subject) {
                subject.setText(state.subject)
                subject.setSelection(state.subject.length)
            }
            subjectLayout.error = state.subjectError?.resolve(requireContext())
            bindSuggestions(state.suggestions)
            if (text.text?.toString() != state.text) {
                text.setText(state.text)
                text.setSelection(state.text.length)
            }
            textLayout.error = state.textError?.resolve(requireContext())
            textLayout.helperText = if (state.showsMinimumHint) {
                getString(R.string.review_text_too_short, TeacherReviewLimits.MIN_TEXT)
            } else null
            if (anonymous.isChecked != state.anonymous) anonymous.isChecked = state.anonymous
            anonymousHint.isVisible = !state.anonymous
            saveButton.setText(if (state.editing) R.string.review_save else R.string.review_send)
            saveButton.isEnabled = state.canSave
        } finally {
            rendering = false
        }
    }

    private fun bindSuggestions(suggestions: List<String>) = with(binding) {
        suggestionsScroll.isVisible = suggestions.isNotEmpty()
        if (suggestions == shownSuggestions) return@with
        shownSuggestions = suggestions
        this.suggestions.removeAllViews()
        suggestions.forEach { name ->
            val chip = layoutInflater.inflate(R.layout.item_review_subject_chip, this.suggestions, false) as Chip
            chip.text = name
            chip.setOnClickListener {
                subject.setText(name)
                subject.setSelection(subject.text?.length ?: 0)
            }
            this.suggestions.addView(chip)
        }
    }

    /** Leaving with unsaved changes asks first; an untouched form simply closes. */
    private fun close() {
        if (!viewModel.hasChanges()) {
            dismiss()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.review_discard_title)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.review_discard) { _, _ -> dismiss() }
            .show()
    }

    override fun onDestroyView() {
        shownSuggestions = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "ReviewEditorBottomSheet"
        private const val MAX_SUBJECT_LINES = 3

        fun newInstance(args: TeacherReviewArgs) = ReviewEditorBottomSheet().apply {
            arguments = bundleOf(TeacherReviewArgs.TEACHER_ISU to args.teacherIsu, TeacherReviewArgs.TEACHER_NAME to args.teacherName)
        }
    }
}
