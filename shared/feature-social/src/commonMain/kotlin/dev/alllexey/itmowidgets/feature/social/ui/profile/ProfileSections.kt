package dev.alllexey.itmowidgets.feature.social.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeading
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFact
import dev.alllexey.itmowidgets.feature.social.presentation.ProfileFactKind
import dev.alllexey.itmowidgets.feature.social.presentation.SocialBlock
import dev.alllexey.itmowidgets.shared.core.sport_detail_fact_description
import dev.alllexey.itmowidgets.shared.core.user_profile_hidden
import dev.alllexey.itmowidgets.shared.core.user_profile_schedule_title
import dev.alllexey.itmowidgets.shared.core.user_profile_sport_title
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit
import dev.alllexey.itmowidgets.shared.designsystem.ic_exercise
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_location_on
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule
import dev.alllexey.itmowidgets.shared.designsystem.ic_school
import dev.alllexey.itmowidgets.shared.designsystem.ic_work
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.friends_title
import dev.alllexey.itmowidgets.shared.feature.social.person_course
import dev.alllexey.itmowidgets.shared.feature.social.person_fact_education
import dev.alllexey.itmowidgets.shared.feature.social.person_fact_position
import dev.alllexey.itmowidgets.shared.feature.social.person_fact_room
import dev.alllexey.itmowidgets.shared.feature.social.person_section_education
import dev.alllexey.itmowidgets.shared.feature.social.person_section_positions
import dev.alllexey.itmowidgets.shared.feature.social.person_section_rooms
import dev.alllexey.itmowidgets.shared.feature.social.teacher_review_write
import dev.alllexey.itmowidgets.shared.feature.social.teacher_reviews_count
import dev.alllexey.itmowidgets.shared.feature.social.teacher_reviews_title
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_friends_open
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_hidden_friends_hint
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_remove_friend
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_schedule_open
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_sport_open
import dev.alllexey.itmowidgets.shared.feature.social.user_profile_widgets_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * One section of facts (`item_profile_facts.xml`): `Должности`, `Где найти` or `Учёба` over its facts as a connected
 * group of informational rows, each read with its category (`Должность: Доцент`).
 */
@Composable
internal fun ProfileFacts(kind: ProfileFactKind, facts: List<ProfileFact>) {
    Column(Modifier.fillMaxWidth().testTag(UserProfileTestTags.facts(kind))) {
        SectionHeading(stringResource(kind.sectionTitle()))
        facts.forEachIndexed { index, fact -> FactRow(fact, GroupPosition.of(index, facts.size)) }
    }
}

@Composable
private fun FactRow(fact: ProfileFact, position: GroupPosition) {
    val description = stringResource(
        CoreRes.string.sport_detail_fact_description,
        stringResource(fact.kind.category()),
        fact.title,
    )
    val subtitle = if (fact.kind == ProfileFactKind.EDUCATION) {
        listOfNotNull(fact.course?.let { stringResource(Res.string.person_course, it) }, fact.detail)
            .joinToString(", ")
    } else {
        fact.detail.orEmpty()
    }
    GroupRow(position, Modifier.semantics(mergeDescendants = true) {}) {
        RowIcon(fact.kind.icon())
        Column(Modifier.weight(1f)) {
            Text(
                fact.title,
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyLarge,
            )
            if (subtitle.isNotEmpty()) SecondLine(subtitle)
        }
    }
}

/**
 * `ITMO.Widgets` (`item_profile_sharing.xml`): `Друзья`, `Расписание` and `Спорт` as one group, open as Backend's
 * viewer-scoped `sharing` allows (the viewer's own schedule and sport always), the privacy hint for someone who is
 * not a friend yet and, for a friend, `Удалить из друзей`.
 */
@Composable
internal fun ProfileSharing(social: SocialBlock, actions: UserProfileActions) {
    val sharing = social.profile.user.sharing
    val scheduleOpen = social.isSelf || sharing.schedule
    val sportOpen = social.isSelf || sharing.sport
    val friends = social.profile.relationship == RelationshipState.FRIENDS
    Column(Modifier.fillMaxWidth()) {
        SectionHeading(stringResource(Res.string.user_profile_widgets_title))
        SharingRow(
            KitRes.drawable.ic_group,
            stringResource(Res.string.friends_title),
            stringResource(Res.string.user_profile_friends_open).takeIf { sharing.friends },
            GroupPosition.First,
            actions.onFriends,
            UserProfileTestTags.FRIENDS,
        )
        SharingRow(
            KitRes.drawable.ic_schedule,
            stringResource(CoreRes.string.user_profile_schedule_title),
            stringResource(Res.string.user_profile_schedule_open).takeIf { scheduleOpen },
            GroupPosition.Middle,
            actions.onSchedule,
            UserProfileTestTags.SCHEDULE,
        )
        SharingRow(
            KitRes.drawable.ic_exercise,
            stringResource(CoreRes.string.user_profile_sport_title),
            stringResource(Res.string.user_profile_sport_open).takeIf { sportOpen },
            GroupPosition.Last,
            actions.onSport,
            UserProfileTestTags.SPORT,
        )
        if (!social.isSelf && !friends && (!scheduleOpen || !sportOpen)) {
            Text(
                stringResource(Res.string.user_profile_hidden_friends_hint),
                Modifier
                    .fillMaxWidth()
                    .padding(start = ItmoTheme.spacing.group, top = ItmoTheme.spacing.compact, end = ItmoTheme.spacing.group)
                    .testTag(UserProfileTestTags.HIDDEN_HINT),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
        if (!social.isSelf && friends) {
            // The kit has no error-toned button yet; this is `Widget.Material3.Button.TextButton` in `colorError`.
            TextButton(
                onClick = actions.onRemoveFriend,
                modifier = Modifier
                    .padding(start = ItmoTheme.spacing.related, top = ItmoTheme.spacing.compact)
                    .testTag(UserProfileTestTags.REMOVE),
                enabled = !social.busy,
                colors = ButtonDefaults.textButtonColors(contentColor = ItmoTheme.colorScheme.error),
            ) { Text(stringResource(Res.string.user_profile_remove_friend)) }
        }
    }
}

/**
 * One entry of `ITMO.Widgets` (`item_profile_entry.xml`): an [openDescription] makes it a target with a chevron;
 * without one it says why with a lock, keeps its surface, fades its content and is no target.
 */
@Composable
private fun SharingRow(
    icon: DrawableResource,
    title: String,
    openDescription: String?,
    position: GroupPosition,
    onOpen: () -> Unit,
    tag: String,
) {
    val open = openDescription != null
    val interaction = if (open) Modifier.clickable(onClick = onOpen) else Modifier.semantics(mergeDescendants = true) {}
    GroupRow(position, interaction.testTag(tag), contentAlpha = if (open) 1f else CLOSED_ALPHA) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(end = ItmoTheme.spacing.compact)) {
            Text(
                title,
                Modifier.fillMaxWidth(),
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyLarge,
            )
            SecondLine(openDescription ?: stringResource(CoreRes.string.user_profile_hidden))
        }
        Icon(
            painterResource(if (open) KitRes.drawable.ic_chevron_right else KitRes.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The reviews heading (`item_profile_section.xml`): `Отзывы` in the accent, the count right after it (TalkBack reads
 * `Отзывы, N`) and `Написать` at the end, on one line in a 48 dp row.
 */
@Composable
internal fun ReviewsHeading(count: Int, canWrite: Boolean, onWrite: () -> Unit) {
    val countDescription = stringResource(Res.string.teacher_reviews_count, count)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(start = ItmoTheme.spacing.cardPadding, top = ItmoTheme.spacing.group, bottom = ItmoTheme.spacing.related)
            .testTag(UserProfileTestTags.REVIEWS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.teacher_reviews_title),
            Modifier.semantics {
                heading()
                if (count > 0) contentDescription = countDescription
            },
            color = ItmoTheme.colorScheme.primary,
            style = ItmoTheme.typography.titleSmall,
        )
        if (count > 0) {
            Text(
                count.toString(),
                Modifier.padding(start = ItmoTheme.spacing.compact).clearAndSetSemantics {},
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        Spacer(Modifier.weight(1f))
        if (canWrite) {
            ProgressButton(
                stringResource(Res.string.teacher_review_write),
                onClick = onWrite,
                modifier = Modifier.testTag(UserProfileTestTags.WRITE_REVIEW),
                style = ProgressButtonStyle.Text,
                icon = painterResource(KitRes.drawable.ic_edit),
            )
        }
    }
}

/** A row of a connected group: the 24 dp symbol, the texts, 56 dp at least; [contentAlpha] fades only the content. */
@Composable
private fun GroupRow(
    position: GroupPosition,
    modifier: Modifier,
    contentAlpha: Float = 1f,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .connectedGroupItem(position)
            .then(modifier)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content)
            .alpha(contentAlpha),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowIcon(icon: DrawableResource) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.padding(end = ItmoTheme.spacing.cardPadding).size(IconSize),
        tint = ItmoTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SecondLine(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(top = SecondLineGap),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodyMedium,
    )
}

private fun ProfileFactKind.sectionTitle(): StringResource = when (this) {
    ProfileFactKind.POSITION -> Res.string.person_section_positions
    ProfileFactKind.ROOM -> Res.string.person_section_rooms
    ProfileFactKind.EDUCATION -> Res.string.person_section_education
}

private fun ProfileFactKind.category(): StringResource = when (this) {
    ProfileFactKind.POSITION -> Res.string.person_fact_position
    ProfileFactKind.ROOM -> Res.string.person_fact_room
    ProfileFactKind.EDUCATION -> Res.string.person_fact_education
}

private fun ProfileFactKind.icon(): DrawableResource = when (this) {
    ProfileFactKind.POSITION -> KitRes.drawable.ic_work
    ProfileFactKind.ROOM -> KitRes.drawable.ic_location_on
    ProfileFactKind.EDUCATION -> KitRes.drawable.ic_school
}

/** A connected group's row is at least 56 dp high. */
private val RowMinHeight = 56.dp
private val IconSize = 24.dp
private val SecondLineGap = 2.dp

/** A closed entry keeps its surface; only its content fades. */
private const val CLOSED_ALPHA = 0.72f

private const val TABULAR_FIGURES = "tnum"
