package dev.alllexey.itmowidgets.feature.social.ui

import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.ui.userDisplayName
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.databinding.ItemProfileFactsBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileHeaderBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileRelationshipBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileSectionBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileSharingBinding
import dev.alllexey.itmowidgets.databinding.ItemTeacherReviewBinding
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFact
import dev.alllexey.itmowidgets.feature.social.presentation.SocialBlock
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState

sealed interface ProfileItem {
    data class Header(val name: String, val pictureUrl: String?, val isu: Int) : ProfileItem
    data class Relationship(val social: SocialBlock) : ProfileItem
    data class Sharing(val social: SocialBlock) : ProfileItem
    data class Facts(val facts: List<ProfileFact>) : ProfileItem
    data class Section(@param:StringRes val titleRes: Int) : ProfileItem
    data class Review(val review: TeacherReview) : ProfileItem
}

data class ProfileActions(
    val onPrimary: () -> Unit = {},
    val onSecondary: () -> Unit = {},
    val onFriends: () -> Unit = {},
    val onSchedule: () -> Unit = {},
    val onSport: () -> Unit = {},
    val onSource: (String) -> Unit = {}
)

/** One page with independently updating blocks; late reviews are appended after all identity facts. */
class UserProfileAdapter(private val actions: ProfileActions = ProfileActions()) :
    ListAdapter<ProfileItem, RecyclerView.ViewHolder>(Diff) {

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    fun submitContent(state: UserProfileUiState.Content, onCommitted: () -> Unit = {}) {
        submitList(buildList {
            add(ProfileItem.Header(state.name, state.pictureUrl, state.isu))
            state.social?.let { social ->
                if (social.isSelf || social.profile.relationship != RelationshipState.BLOCKED) {
                    add(ProfileItem.Relationship(social))
                }
                add(ProfileItem.Sharing(social))
            }
            if (state.facts.isNotEmpty()) add(ProfileItem.Facts(state.facts))
            if (state.reviews.isNotEmpty()) {
                add(ProfileItem.Section(R.string.teacher_reviews_title))
                addAll(state.reviews.map(ProfileItem::Review))
            }
        }, onCommitted)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ProfileItem.Header -> R.layout.item_profile_header
        is ProfileItem.Relationship -> R.layout.item_profile_relationship
        is ProfileItem.Sharing -> R.layout.item_profile_sharing
        is ProfileItem.Facts -> R.layout.item_profile_facts
        is ProfileItem.Section -> R.layout.item_profile_section
        is ProfileItem.Review -> R.layout.item_teacher_review
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            R.layout.item_profile_header -> HeaderHolder(ItemProfileHeaderBinding.inflate(inflater, parent, false))
            R.layout.item_profile_relationship -> RelationshipHolder(ItemProfileRelationshipBinding.inflate(inflater, parent, false))
            R.layout.item_profile_sharing -> SharingHolder(ItemProfileSharingBinding.inflate(inflater, parent, false))
            R.layout.item_profile_facts -> FactsHolder(ItemProfileFactsBinding.inflate(inflater, parent, false))
            R.layout.item_profile_section -> SectionHolder(ItemProfileSectionBinding.inflate(inflater, parent, false))
            R.layout.item_teacher_review -> ReviewHolder(ItemTeacherReviewBinding.inflate(inflater, parent, false))
            else -> error("Unknown profile view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ProfileItem.Header -> (holder as HeaderHolder).bind(item)
            is ProfileItem.Relationship -> (holder as RelationshipHolder).bind(item.social)
            is ProfileItem.Sharing -> (holder as SharingHolder).bind(item.social)
            is ProfileItem.Facts -> (holder as FactsHolder).binding.bindFacts(item.facts)
            is ProfileItem.Section -> (holder as SectionHolder).binding.title.setText(item.titleRes)
            is ProfileItem.Review -> (holder as ReviewHolder).bind(item.review)
        }
    }

    private class HeaderHolder(private val binding: ItemProfileHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ProfileItem.Header) = with(binding) {
            val displayName = root.context.userDisplayName(header.name, header.isu)
            avatar.setUser(displayName, header.pictureUrl)
            name.text = displayName
            meta.text = root.context.getString(R.string.me_isu, header.isu)
        }
    }

    private inner class RelationshipHolder(private val binding: ItemProfileRelationshipBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(social: SocialBlock) = with(binding) {
            val relationship = social.profile.relationship
            relationshipStatus.isVisible = social.isSelf || relationship == RelationshipState.INCOMING
            relationshipStatus.setText(if (social.isSelf) R.string.user_profile_self else R.string.user_status_incoming)
            relationshipStatus.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (social.isSelf) 0 else root.resources.getDimensionPixelSize(R.dimen.design_spacing_compact)
            }
            actions.isVisible = !social.isSelf && relationship != RelationshipState.BLOCKED
            primaryAction.isEnabled = !social.busy
            secondaryAction.isEnabled = !social.busy
            primaryAction.isVisible = !social.isSelf && relationship != RelationshipState.BLOCKED
            secondaryAction.isVisible = !social.isSelf && relationship == RelationshipState.INCOMING
            secondaryAction.setText(R.string.user_profile_reject_request)
            val primaryTitle = when (relationship) {
                RelationshipState.NONE, RelationshipState.BLOCKED -> R.string.user_profile_add_friend
                RelationshipState.OUTGOING -> R.string.user_profile_cancel_request
                RelationshipState.INCOMING -> R.string.user_profile_accept_request
                RelationshipState.FRIENDS -> R.string.user_profile_remove_friend
            }
            primaryAction.applyStyle(primaryTitle, relationship)
            primaryAction.setOnClickListener { this@UserProfileAdapter.actions.onPrimary() }
            secondaryAction.setOnClickListener { this@UserProfileAdapter.actions.onSecondary() }
        }
    }

    private inner class SharingHolder(private val binding: ItemProfileSharingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(social: SocialBlock) = with(binding) {
            val sharing = social.profile.user.sharing
            bindEntry(friendsRow, friendsDescription, friendsTrailing, sharing.friends,
                R.string.user_profile_friends_open, actions.onFriends)
            val scheduleOpen = social.isSelf || sharing.schedule
            val sportOpen = social.isSelf || sharing.sport
            bindEntry(scheduleRow, scheduleDescription, scheduleTrailing, scheduleOpen,
                R.string.user_profile_schedule_open, actions.onSchedule)
            bindEntry(sportRow, sportDescription, sportTrailing, sportOpen,
                R.string.user_profile_sport_open, actions.onSport)
            hiddenHint.isVisible = !social.isSelf && social.profile.relationship != RelationshipState.FRIENDS &&
                (!scheduleOpen || !sportOpen)
        }
    }

    private class FactsHolder(val binding: ItemProfileFactsBinding) : RecyclerView.ViewHolder(binding.root)
    private class SectionHolder(val binding: ItemProfileSectionBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class ReviewHolder(private val binding: ItemTeacherReviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(review: TeacherReview) = with(binding) {
            subject.text = review.subject
            subject.isVisible = review.subject != null
            date.text = review.written?.text(root.context)
            date.isVisible = review.written != null
            text.text = review.text
            val copy = review.origin as? ReviewOrigin.Reviews
            source.isVisible = copy != null
            source.text = copy?.let {
                it.sourceTitle?.let { title -> root.context.getString(R.string.teacher_review_source, title) }
                    ?: root.context.getString(R.string.teacher_review_source_default)
            }
            source.setOnClickListener(copy?.let { origin -> View.OnClickListener { actions.onSource(origin.sourceUrl) } })
        }
    }

    private fun bindEntry(
        row: View,
        description: TextView,
        trailing: ImageView,
        open: Boolean,
        @StringRes openDescription: Int,
        onOpen: () -> Unit
    ) {
        description.setText(if (open) openDescription else R.string.user_profile_hidden)
        trailing.setImageResource(if (open) R.drawable.ic_chevron_right else R.drawable.ic_lock)
        row.setOnClickListener(if (open) { _ -> onOpen() } else null)
        row.isClickable = open
        row.isFocusable = open
        row.isEnabled = open
        row.alpha = if (open) 1f else 0.72f
    }

    /** One button, two weights: filled for joining, tonal for stepping back. */
    private fun MaterialButton.applyStyle(@StringRes title: Int, relationship: RelationshipState) {
        setText(title)
        val filled = relationship == RelationshipState.NONE || relationship == RelationshipState.INCOMING
        val colors = context.color
        backgroundTintList = ColorStateList.valueOf(if (filled) colors.primary else colors.secondaryContainer)
        setTextColor(if (filled) colors.onPrimary else colors.onSecondaryContainer)
    }

    private object Diff : DiffUtil.ItemCallback<ProfileItem>() {
        override fun areItemsTheSame(old: ProfileItem, new: ProfileItem): Boolean = when {
            old is ProfileItem.Review && new is ProfileItem.Review -> old.review.id == new.review.id
            else -> old::class == new::class
        }

        override fun areContentsTheSame(old: ProfileItem, new: ProfileItem): Boolean = old == new
    }
}

/** Insets depend on neighbouring rows, not recycled view margins, so insertions preserve existing geometry. */
internal class ProfileItemSpacing : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.set(0, 0, 0, 0)
        val position = parent.getChildAdapterPosition(view)
        val items = (parent.adapter as? UserProfileAdapter)?.currentList ?: return
        val item = items.getOrNull(position) ?: return
        val previous = items.getOrNull(position - 1) ?: return
        val spacing = when {
            item is ProfileItem.Review -> if (previous is ProfileItem.Review) R.dimen.design_spacing_compact else return
            // The section's own 12 dp top padding completes the 16 dp group gap.
            item is ProfileItem.Section -> R.dimen.design_spacing_related
            else -> R.dimen.design_spacing_group
        }
        outRect.top = parent.resources.getDimensionPixelSize(spacing)
    }
}
