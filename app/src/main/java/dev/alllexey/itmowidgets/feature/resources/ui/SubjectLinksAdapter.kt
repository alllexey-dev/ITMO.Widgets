package dev.alllexey.itmowidgets.feature.resources.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemSubjectLinkBinding
import dev.alllexey.itmowidgets.databinding.ItemSubjectLinkSectionBinding
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkSection
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksUiState
import dev.alllexey.itmowidgets.feature.resources.presentation.badge
import dev.alllexey.itmowidgets.core.ui.iconRes
import dev.alllexey.itmowidgets.core.ui.label
import dev.alllexey.itmowidgets.core.ui.title
import java.net.URI
import java.util.Locale

internal sealed interface LinkRow {
    val key: String

    data class Header(val title: UiText, val iconRes: Int) : LinkRow {
        override val key: String get() = "header:$iconRes:$title"
    }

    data class Item(
        val link: SubjectLink,
        val pinned: Boolean,
        val canVote: Boolean,
        val previous: Boolean,
    ) : LinkRow {
        override val key: String get() = "link:${link.id}"
    }
}

/** Sections become a header row followed by their links; chats are titled «Чаты». */
internal fun SubjectLinksUiState.linkRows(): List<LinkRow> {
    val snapshot = content ?: return emptyList()
    return sections.flatMap { section ->
        val header = when (section) {
            is LinkSection.Category -> LinkRow.Header(
                if (section.category == LinkCategory.CHAT) UiText.Resource(R.string.links_chats) else section.category.title(),
                section.category.iconRes(),
            )
            is LinkSection.Previous -> LinkRow.Header(UiText.Resource(R.string.links_previous), R.drawable.ic_history)
        }
        listOf(header) + section.links.map { link ->
            LinkRow.Item(link, link.id == snapshot.pinnedId, canVote, section is LinkSection.Previous)
        }
    }
}

/** The site without `www.`, or the raw text when it is not a URI. */
internal fun SubjectLink.host(): String =
    runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: url

internal fun SubjectLink.displayTitle(): String = title ?: host()

/**
 * The second line: the site when the title hides it, who sees an own link or the author's group of
 * another's one, the study year of a past link, the pin, and the review state only the owner sees.
 */
internal fun Context.linkMeta(link: SubjectLink, pinned: Boolean, previous: Boolean): String = listOfNotNull(
    link.host().takeIf { link.title != null },
    if (link.isMine) link.visibility.label(link.audienceLabel).resolve(this)
    else link.author?.primaryGroup()?.name ?: link.audienceLabel,
    link.scope.periodKey.studyYear().takeIf { previous },
    getString(R.string.links_pinned).takeIf { pinned },
    link.status.badge()?.resolve(this)?.takeIf { link.isMine },
).joinToString(" · ")

/** `2025-1` is the 2025/26 study year. */
private fun String.studyYear(): String? {
    val year = substringBefore('-').toIntOrNull() ?: return null
    return "$year/${(year + 1) % 100}"
}

internal class SubjectLinksAdapter(
    private val onOpen: (SubjectLink) -> Unit,
    private val onActions: (SubjectLink) -> Unit,
    private val onVote: (SubjectLink, Boolean) -> Unit,
) : ListAdapter<LinkRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is LinkRow.Header -> TYPE_HEADER
        is LinkRow.Item -> TYPE_LINK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) HeaderHolder(ItemSubjectLinkSectionBinding.inflate(inflater, parent, false))
        else LinkHolder(ItemSubjectLinkBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is LinkRow.Header -> (holder as HeaderHolder).bind(row)
            is LinkRow.Item -> (holder as LinkHolder).bind(row)
        }
    }

    private class HeaderHolder(private val binding: ItemSubjectLinkSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: LinkRow.Header) {
            binding.title.text = row.title.resolve(binding.root.context)
            binding.title.setCompoundDrawablesRelativeWithIntrinsicBounds(row.iconRes, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(binding.title,
                ColorStateList.valueOf(binding.root.context.color.onSurfaceVariant))
        }
    }

    private inner class LinkHolder(private val binding: ItemSubjectLinkBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: LinkRow.Item) = with(binding) {
            val context = root.context
            val link = row.link
            title.text = link.displayTitle()
            meta.text = context.linkMeta(link, row.pinned, row.previous)
            voteColumn.bind(link, row.canVote) { up -> onVote(link, up) }
            root.setBackgroundResource(if (link.isMine) R.drawable.bg_subject_link_row_own else R.drawable.bg_subject_link_row)
            root.setOnClickListener { onOpen(link) }
            root.setOnLongClickListener { onActions(link); true }
            ViewCompat.replaceAccessibilityAction(root, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                context.getString(R.string.links_actions), null)
        }
    }

    private object Diff : DiffUtil.ItemCallback<LinkRow>() {
        override fun areItemsTheSame(oldItem: LinkRow, newItem: LinkRow) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: LinkRow, newItem: LinkRow) = oldItem == newItem
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_LINK = 1
    }
}
