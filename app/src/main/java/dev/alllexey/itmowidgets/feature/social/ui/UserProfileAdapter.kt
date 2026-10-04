package dev.alllexey.itmowidgets.feature.social.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.core.ui.GroupPosition
import dev.alllexey.itmowidgets.core.ui.bindGroupPosition
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.userDisplayName
import dev.alllexey.itmowidgets.databinding.ItemOwnTeacherReviewBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileEntryBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileFactsBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileHeaderBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileSectionBinding
import dev.alllexey.itmowidgets.databinding.ItemProfileSharingBinding
import dev.alllexey.itmowidgets.databinding.ItemTeacherReviewBinding
import dev.alllexey.itmowidgets.databinding.ItemTeacherSummaryBinding
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFact
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFactKind
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileHeadline
import dev.alllexey.itmowidgets.feature.social.presentation.SocialBlock
import dev.alllexey.itmowidgets.feature.social.presentation.UserProfileUiState
import java.util.Locale

sealed interface ProfileItem {
    /** The hero card: the person, the ISU number and, with a social block, the friendship and its buttons. */
    data class Header(
        val name: String,
        val pictureUrl: String?,
        val isu: Int,
        val headline: ProfileHeadline?,
        val social: SocialBlock? = null,
    ) : ProfileItem
    /** «ITMO.Widgets»: the person's friends, schedule and sport, and removing a friend. */
    data class Sharing(val social: SocialBlock) : ProfileItem
    /** One heading with the facts of one [kind]: «Должности», «Где найти» or «Учёба». */
    data class Facts(val kind: ProfileFactKind, val facts: List<ProfileFact>) : ProfileItem
    /** The reviews heading: «Отзывы», the count and «Написать». */
    data class Section(@param:StringRes val titleRes: Int, val count: Int = 0, @param:StringRes val actionRes: Int? = null) : ProfileItem
    /** Backend's AI summary of the reviews; [expanded] shows its scales. */
    data class Summary(val summary: TeacherSummary, val expanded: Boolean) : ProfileItem
    data class OwnReview(val review: OwnTeacherReview, val busy: Boolean, val position: GroupPosition = GroupPosition.SINGLE) : ProfileItem
    data class Review(
        val review: TeacherReview,
        val canVote: Boolean,
        val canReport: Boolean,
        val busy: Boolean,
        val position: GroupPosition = GroupPosition.SINGLE,
    ) : ProfileItem
}

data class ProfileActions(
    val onPrimary: () -> Unit = {},
    val onSecondary: () -> Unit = {},
    val onFriends: () -> Unit = {},
    val onSchedule: () -> Unit = {},
    val onSport: () -> Unit = {},
    val onCopyIsu: (Int) -> Unit = {},
    val onSource: (String) -> Unit = {},
    val onWriteReview: () -> Unit = {},
    val onEditReview: () -> Unit = {},
    val onDeleteReview: () -> Unit = {},
    val onVote: (reviewId: String, up: Boolean) -> Unit = { _, _ -> },
    val onReport: (reviewId: String) -> Unit = {},
    val onAuthor: (isu: Int) -> Unit = {},
    val onToggleSummary: () -> Unit = {}
)

/**
 * One page with independently updating blocks: the hero card, the facts and ITMO.Widgets sections, then the reviews.
 * Every section is a heading over a connected group; late reviews are appended after all identity sections.
 */
class UserProfileAdapter(private val actions: ProfileActions = ProfileActions()) :
    ListAdapter<ProfileItem, RecyclerView.ViewHolder>(Diff) {

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    fun submitContent(state: UserProfileUiState.Content, onCommitted: () -> Unit = {}) {
        submitList(buildList {
            add(ProfileItem.Header(state.name, state.pictureUrl, state.isu, state.headline, state.social))
            val byKind = state.facts.groupBy(ProfileFact::kind)
            listOf(ProfileFactKind.POSITION, ProfileFactKind.ROOM).forEach { kind ->
                byKind[kind]?.let { add(ProfileItem.Facts(kind, it)) }
            }
            state.social?.let { add(ProfileItem.Sharing(it)) }
            byKind[ProfileFactKind.EDUCATION]?.let { add(ProfileItem.Facts(ProfileFactKind.EDUCATION, it)) }
            state.reviews?.let { reviews ->
                add(ProfileItem.Section(R.string.teacher_reviews_title, reviews.count,
                    R.string.teacher_review_write.takeIf { reviews.canWrite }))
                reviews.summary?.let { add(ProfileItem.Summary(it, reviews.summaryExpanded)) }
                // The own review is a group of its own; the others follow as one group under it.
                reviews.mine?.let { add(ProfileItem.OwnReview(it, busy = reviews.busyId == it.id, GroupPosition.SINGLE)) }
                reviews.items.forEachIndexed { index, review ->
                    add(ProfileItem.Review(review, reviews.canVote, reviews.canReport, busy = reviews.busyId == review.id,
                        GroupPosition.of(index, reviews.items.size)))
                }
            }
        }, onCommitted)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ProfileItem.Header -> R.layout.item_profile_header
        is ProfileItem.Sharing -> R.layout.item_profile_sharing
        is ProfileItem.Facts -> R.layout.item_profile_facts
        is ProfileItem.Section -> R.layout.item_profile_section
        is ProfileItem.Summary -> R.layout.item_teacher_summary
        is ProfileItem.OwnReview -> R.layout.item_own_teacher_review
        is ProfileItem.Review -> R.layout.item_teacher_review
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            R.layout.item_profile_header -> HeaderHolder(ItemProfileHeaderBinding.inflate(inflater, parent, false))
            R.layout.item_profile_sharing -> SharingHolder(ItemProfileSharingBinding.inflate(inflater, parent, false))
            R.layout.item_profile_facts -> FactsHolder(ItemProfileFactsBinding.inflate(inflater, parent, false))
            R.layout.item_profile_section -> SectionHolder(ItemProfileSectionBinding.inflate(inflater, parent, false))
            R.layout.item_teacher_summary -> SummaryHolder(ItemTeacherSummaryBinding.inflate(inflater, parent, false))
            R.layout.item_own_teacher_review -> OwnReviewHolder(ItemOwnTeacherReviewBinding.inflate(inflater, parent, false))
            R.layout.item_teacher_review -> ReviewHolder(ItemTeacherReviewBinding.inflate(inflater, parent, false))
            else -> error("Unknown profile view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ProfileItem.Header -> (holder as HeaderHolder).bind(item)
            is ProfileItem.Sharing -> (holder as SharingHolder).bind(item.social)
            is ProfileItem.Facts -> (holder as FactsHolder).binding.bindFacts(item.kind, item.facts)
            is ProfileItem.Section -> (holder as SectionHolder).bind(item)
            is ProfileItem.Summary -> (holder as SummaryHolder).binding.bind(item.summary, item.expanded, actions.onToggleSummary)
            is ProfileItem.OwnReview -> (holder as OwnReviewHolder).binding.bind(item, actions)
            is ProfileItem.Review -> (holder as ReviewHolder).binding.bind(item, actions)
        }
    }

    private inner class HeaderHolder(private val binding: ItemProfileHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ProfileItem.Header) = with(binding) {
            val context = root.context
            val displayName = context.userDisplayName(header.name, header.isu)
            avatar.setUser(displayName, header.pictureUrl)
            name.text = displayName
            val line = header.headline?.text(context)
            headline.text = line
            headline.isVisible = line != null
            isuValue.text = String.format(Locale.ROOT, "%d", header.isu)
            isu.contentDescription = context.getString(R.string.person_isu_copy, header.isu)
            isu.setOnClickListener { this@UserProfileAdapter.actions.onCopyIsu(header.isu) }
            bindFriendship(header.social)
        }

        /**
         * A friend and the viewer get a badge; an open request says so above its buttons; anyone else gets
         * `Добавить в друзья`. Nothing for a blocked person or without the connection.
         */
        private fun bindFriendship(social: SocialBlock?) = with(binding) {
            val relationship = social?.profile?.relationship
            val badgeText = when {
                social == null -> null
                social.isSelf -> R.string.user_profile_self
                relationship == RelationshipState.FRIENDS -> R.string.user_profile_friend_badge
                else -> null
            }
            badge.isVisible = badgeText != null
            badgeText?.let(badge::setText)
            val status = when {
                social == null || social.isSelf -> null
                relationship == RelationshipState.INCOMING -> R.string.user_status_incoming
                relationship == RelationshipState.OUTGOING -> R.string.user_status_outgoing
                else -> null
            }
            relationshipStatus.isVisible = status != null
            status?.let(relationshipStatus::setText)
            val primaryTitle = when {
                social == null || social.isSelf -> null
                relationship == RelationshipState.NONE -> R.string.user_profile_add_friend
                relationship == RelationshipState.OUTGOING -> R.string.user_profile_cancel_request
                relationship == RelationshipState.INCOMING -> R.string.user_profile_accept_request
                else -> null
            }
            actions.isVisible = primaryTitle != null
            // A status line already separates the buttons from the person.
            actions.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = root.resources.getDimensionPixelSize(
                    if (status != null) R.dimen.design_spacing_compact else R.dimen.design_spacing_group)
            }
            primaryAction.isEnabled = social?.busy != true
            secondaryAction.isEnabled = social?.busy != true
            secondaryAction.isVisible = relationship == RelationshipState.INCOMING && social?.isSelf == false
            secondaryAction.setText(R.string.user_profile_reject_request)
            primaryTitle?.let { primaryAction.applyStyle(it, checkNotNull(relationship)) }
            primaryAction.setOnClickListener { this@UserProfileAdapter.actions.onPrimary() }
            secondaryAction.setOnClickListener { this@UserProfileAdapter.actions.onSecondary() }
        }
    }

    private inner class SharingHolder(private val binding: ItemProfileSharingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(social: SocialBlock) = with(binding) {
            heading.title.setText(R.string.user_profile_widgets_title)
            val sharing = social.profile.user.sharing
            val scheduleOpen = social.isSelf || sharing.schedule
            val sportOpen = social.isSelf || sharing.sport
            friendsRow.bindEntry(R.drawable.ic_group, R.string.friends_title, sharing.friends,
                R.string.user_profile_friends_open, GroupPosition.FIRST, actions.onFriends)
            scheduleRow.bindEntry(R.drawable.ic_schedule, R.string.user_profile_schedule_title, scheduleOpen,
                R.string.user_profile_schedule_open, GroupPosition.MIDDLE, actions.onSchedule)
            sportRow.bindEntry(R.drawable.ic_exercise, R.string.user_profile_sport_title, sportOpen,
                R.string.user_profile_sport_open, GroupPosition.LAST, actions.onSport)
            val friends = social.profile.relationship == RelationshipState.FRIENDS
            hiddenHint.isVisible = !social.isSelf && !friends && (!scheduleOpen || !sportOpen)
            removeFriend.isVisible = !social.isSelf && friends
            removeFriend.isEnabled = !social.busy
            removeFriend.setOnClickListener { actions.onPrimary() }
        }
    }

    private class FactsHolder(val binding: ItemProfileFactsBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class SectionHolder(private val binding: ItemProfileSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(section: ProfileItem.Section) = with(binding) {
            val context = root.context
            title.setText(section.titleRes)
            count.text = section.count.toString()
            count.isVisible = section.count > 0
            title.contentDescription = if (section.count > 0) context.getString(R.string.teacher_reviews_count, section.count) else null
            action.isVisible = section.actionRes != null
            section.actionRes?.let(action::setText)
            action.setOnClickListener(section.actionRes?.let { View.OnClickListener { actions.onWriteReview() } })
        }
    }

    private class SummaryHolder(val binding: ItemTeacherSummaryBinding) : RecyclerView.ViewHolder(binding.root)
    private class OwnReviewHolder(val binding: ItemOwnTeacherReviewBinding) : RecyclerView.ViewHolder(binding.root)
    private class ReviewHolder(val binding: ItemTeacherReviewBinding) : RecyclerView.ViewHolder(binding.root)

    /** An open entry leads further with a chevron; a closed one says why with a lock and is not a target. */
    private fun ItemProfileEntryBinding.bindEntry(
        @DrawableRes icon: Int,
        @StringRes title: Int,
        open: Boolean,
        @StringRes openDescription: Int,
        position: GroupPosition,
        onOpen: () -> Unit
    ) {
        this.icon.setImageResource(icon)
        entryTitle.setText(title)
        description.setText(if (open) openDescription else R.string.user_profile_hidden)
        trailing.setImageResource(if (open) R.drawable.ic_chevron_right else R.drawable.ic_lock)
        root.setOnClickListener(if (open) { _ -> onOpen() } else null)
        root.isClickable = open
        root.isFocusable = open
        root.isEnabled = open
        root.bindGroupPosition(position)
        // The row keeps its surface; only its content fades.
        listOf(this.icon, entryTitle, description, trailing).forEach { it.alpha = if (open) 1f else CLOSED_ALPHA }
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
            old is ProfileItem.Facts && new is ProfileItem.Facts -> old.kind == new.kind
            else -> old::class == new::class
        }

        override fun areContentsTheSame(old: ProfileItem, new: ProfileItem): Boolean = old == new
    }

    private companion object {
        const val CLOSED_ALPHA = 0.72f
    }
}

/**
 * The gaps the items do not carry themselves: the AI card stands 8 dp above the first review, and the own review's
 * group 16 dp above the others' group. Insets depend on neighbouring rows, not recycled view margins, so insertions
 * preserve existing geometry.
 */
internal class ProfileItemSpacing : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        outRect.set(0, 0, 0, 0)
        val position = parent.getChildAdapterPosition(view)
        val items = (parent.adapter as? UserProfileAdapter)?.currentList ?: return
        val item = items.getOrNull(position) ?: return
        val previous = items.getOrNull(position - 1) ?: return
        val gap = when {
            (item is ProfileItem.Review || item is ProfileItem.OwnReview) && previous is ProfileItem.Summary -> R.dimen.design_spacing_compact
            item is ProfileItem.Review && previous is ProfileItem.OwnReview -> R.dimen.design_spacing_group
            else -> return
        }
        outRect.top = parent.resources.getDimensionPixelSize(gap)
    }
}

/** «Преподаватель» or «M3234, 2 курс»; null when there is nothing to say. */
internal fun ProfileHeadline.text(context: Context): String? = when (this) {
    is ProfileHeadline.Position -> role
    is ProfileHeadline.Group -> listOfNotNull(name, course?.let { context.getString(R.string.person_course, it) }).joinToString(", ")
}.ifEmpty { null }
