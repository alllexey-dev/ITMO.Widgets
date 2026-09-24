package dev.alllexey.itmowidgets.feature.resources.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TextAppearanceSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.ui.lessonTypeNameRes
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.util.HttpsNavigationPolicy
import dev.alllexey.itmowidgets.databinding.ItemLinkAudienceOptionBinding
import dev.alllexey.itmowidgets.databinding.SheetLinkEditorBinding
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkAudienceOption
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEvent
import dev.alllexey.itmowidgets.core.ui.linkIconRes
import dev.alllexey.itmowidgets.core.ui.title
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import dev.alllexey.itmowidgets.core.ui.expandToContent

/** Adds a link or edits an own one: address, category chips, optional title and who sees it. */
@AndroidEntryPoint
class LinkEditorBottomSheet : BottomSheetDialogFragment() {
    private var _binding: SheetLinkEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LinkEditorViewModel by viewModels()
    /** Programmatic updates of the fields must not read as the user's choice. */
    private var rendering = false
    private var shownCategory: LinkCategory? = null
    /** The rows currently in the «Кто видит» group by view ID; rebuilt only when the offer changes. */
    private var shownAudiences: Pair<List<LinkAudienceOption>, Boolean>? = null
    private val audienceRows = mutableMapOf<Int, LinkAudienceOption>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetLinkEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        @Suppress("DEPRECATION")
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        expandToContent()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        // Single-line fields keep the keyboard action; long addresses and titles still wrap instead of scrolling away.
        url.setHorizontallyScrolling(false)
        url.maxLines = MAX_URL_LINES
        // 120 characters take up to six lines on a narrow screen at a large font.
        name.setHorizontallyScrolling(false)
        name.maxLines = MAX_TITLE_LINES
        url.doAfterTextChanged { if (!rendering) viewModel.onUrlChanged(it?.toString().orEmpty()) }
        // The stock clear_text icon hides without focus; here it stays while there is text to clear.
        urlLayout.setEndIconOnClickListener { url.text = null }
        name.doAfterTextChanged { if (!rendering) viewModel.onTitleChanged(it?.toString().orEmpty()) }
        categories.setOnCheckedStateChangeListener { _, ids ->
            if (rendering) return@setOnCheckedStateChangeListener
            ids.firstOrNull()?.let(::categoryOf)?.let(viewModel::onCategorySelected)
        }
        visibility.setOnCheckedChangeListener { _, id ->
            if (!rendering) audienceRows[id]?.let(viewModel::onAudienceSelected)
        }
        saveButton.setOnClickListener { viewModel.save() }
        viewModel.uiState.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach(::render)
            .launchIn(viewLifecycleOwner.lifecycleScope)
        viewModel.events.flowWithLifecycle(viewLifecycleOwner.lifecycle).onEach { event ->
            when (event) {
                LinkEvent.Saved, LinkEvent.Done -> dismiss()
                is LinkEvent.Failed -> Snackbar.make(root, event.text.resolve(requireContext()), Snackbar.LENGTH_SHORT).show()
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
        if (savedInstanceState == null && !viewModel.uiState.value.editing) pasteOnFirstFocus(view)
    }

    private fun render(state: LinkEditorUiState) = with(binding) {
        rendering = true
        try {
            title.setText(if (state.editing) R.string.links_editor_edit else R.string.links_editor_new)
            if (url.text?.toString() != state.url) {
                url.setText(state.url)
                url.setSelection(state.url.length)
            }
            urlLayout.isEndIconVisible = state.url.isNotEmpty()
            urlLayout.error = state.urlError?.resolve(requireContext())
            bindCategory(state.category)
            root.findViewById<com.google.android.material.chip.Chip>(chipIdOf(LinkCategory.CHAT))
                .setChipIconResource(linkIconRes(LinkCategory.CHAT, state.url))
            nameLayout.hint = state.category?.title()?.resolve(requireContext()) ?: getString(R.string.links_name_hint)
            if (name.text?.toString() != state.title) name.setText(state.title)
            nameLayout.error = state.titleError?.resolve(requireContext())
            bindAudiences(state)
            saveButton.isEnabled = state.canSave
        } finally {
            rendering = false
        }
    }

    private fun bindCategory(category: LinkCategory?) = with(binding) {
        if (category == null) categories.clearCheck() else categories.check(chipIdOf(category))
        if (category == shownCategory) return@with
        shownCategory = category
        // A guessed category may sit past the edge of the chip row.
        category?.let { root.findViewById<View>(chipIdOf(it)) }?.let { chip ->
            categoriesScroll.post { categoriesScroll.smoothScrollTo((chip.left - categoriesScroll.paddingStart).coerceAtLeast(0), 0) }
        }
    }

    /** Without the connection only «Только я» is offered and the line below says why. */
    private fun bindAudiences(state: LinkEditorUiState) = with(binding) {
        val offer = state.options to state.premoderation
        if (offer != shownAudiences) {
            shownAudiences = offer
            visibility.clearCheck()
            visibility.removeAllViews()
            audienceRows.clear()
            state.options.forEach { option ->
                val row = ItemLinkAudienceOptionBinding.inflate(layoutInflater, visibility, false).root
                row.id = View.generateViewId()
                row.text = audienceText(option, state.premoderation)
                audienceRows[row.id] = option
                visibility.addView(row)
            }
        }
        val selected = audienceRows.entries.first { it.value == state.selected }.key
        if (visibility.checkedRadioButtonId != selected) visibility.check(selected)
        connectionHint.isVisible = state.options.size == 1
    }

    /** A flow is its schedule name over the kind of classes; «Все» notes the review when there is one. */
    private fun audienceText(option: LinkAudienceOption, premoderation: Boolean): CharSequence = when (option) {
        LinkAudienceOption.Private -> getString(R.string.links_visibility_private)
        is LinkAudienceOption.Flow -> twoLines(option.audience.label, getString(lessonTypeNameRes(option.audience.typeId)))
        LinkAudienceOption.All -> twoLines(getString(R.string.links_visibility_all),
            getString(R.string.links_visibility_all_review).takeIf { premoderation })
    }

    private fun twoLines(title: String, subtitle: String?): CharSequence {
        if (subtitle == null) return title
        val appearance = TypedValue().also {
            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.textAppearanceBodyMedium, it, true)
        }.resourceId
        val color = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        return SpannableStringBuilder(title).append('\n').apply {
            val start = length
            append(subtitle)
            setSpan(TextAppearanceSpan(requireContext(), appearance), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Clipboard reads need window focus, which a sheet only gets once it is shown. */
    private fun pasteOnFirstFocus(view: View) {
        view.viewTreeObserver.addOnWindowFocusChangeListener(object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) return
                val listener = this
                view.post { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
                if (_binding != null) pasteClipboardLink()
            }
        })
    }

    private fun pasteClipboardLink() {
        if (viewModel.uiState.value.url.isNotEmpty()) return
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java) ?: return
        val description = clipboard.primaryClipDescription ?: return
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
            !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) return
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            ?.coerceToText(requireContext())?.toString()?.trim() ?: return
        if (text.any(Char::isWhitespace) || !HttpsNavigationPolicy.isNavigable(text)) return
        viewModel.onUrlChanged(text)
    }

    private fun categoryOf(chipId: Int): LinkCategory? = LinkCategory.entries.firstOrNull { chipIdOf(it) == chipId }

    private fun chipIdOf(category: LinkCategory): Int = when (category) {
        LinkCategory.SCORES -> R.id.category_scores
        LinkCategory.QUEUE -> R.id.category_queue
        LinkCategory.MATERIALS -> R.id.category_materials
        LinkCategory.TASKS -> R.id.category_tasks
        LinkCategory.RECORDINGS -> R.id.category_recordings
        LinkCategory.NOTES -> R.id.category_notes
        LinkCategory.EXAM -> R.id.category_exam
        LinkCategory.CHAT -> R.id.category_chat
        LinkCategory.OTHER -> R.id.category_other
    }

    override fun onDestroyView() {
        shownAudiences = null
        audienceRows.clear()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "LinkEditorBottomSheet"
        private const val MAX_URL_LINES = 4
        private const val MAX_TITLE_LINES = 6

        fun newInstance(args: SubjectLinksArgs, linkId: String? = null) =
            LinkEditorBottomSheet().apply { arguments = args.toArguments(linkId) }
    }
}
