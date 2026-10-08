package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.displayTitle
import dev.alllexey.itmowidgets.core.resources.host
import dev.alllexey.itmowidgets.core.resources.icon
import dev.alllexey.itmowidgets.core.resources.label
import dev.alllexey.itmowidgets.core.resources.linkIcon
import dev.alllexey.itmowidgets.core.resources.title
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.url.StrictUri
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeading
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeadingSpacing
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.components.rows.LinkRow
import dev.alllexey.itmowidgets.designsystem.components.rows.Vote
import dev.alllexey.itmowidgets.designsystem.components.rows.VoteLabels
import dev.alllexey.itmowidgets.designsystem.components.rows.VotePill
import dev.alllexey.itmowidgets.designsystem.icons.drawable
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.ui.preview.RecordbookPreviewSamples
import dev.alllexey.itmowidgets.shared.core.links_actions
import dev.alllexey.itmowidgets.shared.core.links_add
import dev.alllexey.itmowidgets.shared.core.links_chats
import dev.alllexey.itmowidgets.shared.core.links_mine
import dev.alllexey.itmowidgets.shared.core.links_score
import dev.alllexey.itmowidgets.shared.core.links_title
import dev.alllexey.itmowidgets.shared.core.links_vote_down
import dev.alllexey.itmowidgets.shared.core.links_vote_up
import dev.alllexey.itmowidgets.shared.designsystem.ic_add
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.links_all_count
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_link_lms
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * A link of the short list under «Ссылки» (2.2's `SubjectHubAdapter.LinkHolder.bindLink`): the category's icon, the
 * title or the category's name, the site under it, then «моя» for an own link or the vote pill of another student's,
 * with arrows only while [SubjectHubItem.Link.canVote]. A tap opens the link, a long press its actions.
 */
@Composable
fun SubjectLinkItem(
    item: SubjectHubItem.Link,
    onOpen: (String) -> Unit,
    onActions: (SubjectLink) -> Unit,
    onVote: (SubjectLink, Boolean) -> Unit,
) {
    val link = item.link
    LinkRow(
        title = link.title ?: link.category.title().asString(),
        onClick = { onOpen(link.url) },
        modifier = Modifier.connectedGroupItem(item.position),
        icon = painterResource(link.category.icon().drawable),
        caption = link.host(),
        ownBadge = if (link.isMine) stringResource(CoreRes.string.links_mine) else null,
        onLongClick = { onActions(link) },
        longClickLabel = stringResource(CoreRes.string.links_actions),
        votes = if (link.isMine) null else {
            { LinkVotes(link, if (item.canVote) { up -> onVote(link, up) } else null) }
        },
    )
}

/** The MyITMO LMS page of the subject: «LMS» over its site; it opens and has no actions. */
@Composable
fun SubjectLmsItem(item: SubjectHubItem.Lms, onOpen: (String) -> Unit) {
    LinkRow(
        title = stringResource(Res.string.subject_link_lms),
        onClick = { onOpen(item.url) },
        modifier = Modifier.connectedGroupItem(item.position),
        icon = painterResource(LinkCategory.MATERIALS.icon().drawable),
        caption = StrictUri.parse(item.url)?.host?.removePrefix(WWW),
    )
}

/**
 * A chat of the subject (2.2's `LinkHolder.bindChat`): the messenger's icon, the title or the site, and who sees it; an
 * own chat carries «моя». A tap opens the chat, a long press its actions.
 */
@Composable
fun SubjectChatItem(item: SubjectHubItem.Chat, onOpen: (String) -> Unit, onActions: (SubjectLink) -> Unit) {
    val link = item.link
    LinkRow(
        title = link.displayTitle(),
        onClick = { onOpen(link.url) },
        modifier = Modifier.connectedGroupItem(item.position),
        icon = painterResource(linkIcon(link.category, link.url).drawable),
        caption = link.visibility.label(link.audienceLabel).asString(),
        ownBadge = if (link.isMine) stringResource(CoreRes.string.links_mine) else null,
        onLongClick = { onActions(link) },
        longClickLabel = stringResource(CoreRes.string.links_actions),
    )
}

/** «Все ссылки, N»: no symbol of its own, but the text stays in line with the link titles above it. */
@Composable
fun SubjectAllLinksRow(item: SubjectHubItem.AllLinks, onClick: () -> Unit) {
    SubjectActionRow(
        text = stringResource(Res.string.links_all_count, item.count),
        position = item.position,
        onClick = onClick,
        icon = null,
        keepIconSpace = true,
        trailing = painterResource(KitRes.drawable.ic_chevron_right),
    )
}

/** «Добавить ссылку» in place of «Все ссылки» while the subject has no links; it leads nowhere, so no chevron. */
@Composable
fun SubjectAddLinkRow(item: SubjectHubItem.AddLink, onClick: () -> Unit) {
    SubjectActionRow(
        text = stringResource(CoreRes.string.links_add),
        position = item.position,
        onClick = onClick,
        icon = painterResource(KitRes.drawable.ic_add),
    )
}

/**
 * The row that ends a group and leads further (2.2's `item_group_action_row.xml` as `SubjectHubAdapter.ActionHolder`
 * binds it): an optional decorative [icon] or its empty place ([keepIconSpace]), the text and an optional [trailing]
 * symbol. The kit's `GroupActionRow` always draws an icon and a chevron, which the subject page's three rows do not.
 */
@Composable
internal fun SubjectActionRow(
    text: String,
    position: GroupPosition,
    onClick: () -> Unit,
    icon: Painter? = null,
    keepIconSpace: Boolean = false,
    trailing: Painter? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .connectedGroupItem(position)
            .heightIn(min = ActionRowMinHeight)
            .clickable(onClick = onClick)
            .padding(
                start = ItmoTheme.spacing.cardPadding,
                top = ItmoTheme.spacing.compact,
                end = ItmoTheme.spacing.content,
                bottom = ItmoTheme.spacing.compact,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconModifier = Modifier.padding(end = ItmoTheme.spacing.cardPadding).size(ActionIconSize)
        when {
            icon != null -> Icon(icon, null, iconModifier, tint = ItmoTheme.colorScheme.onSurfaceVariant)
            keepIconSpace -> Spacer(iconModifier)
        }
        Text(
            text,
            Modifier.weight(1f),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodyLarge,
        )
        if (trailing != null) {
            Icon(
                trailing,
                contentDescription = null,
                modifier = Modifier.padding(start = ItmoTheme.spacing.compact).size(ActionIconSize),
                tint = ItmoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The pill of another student's link: arrows while [onVote] is set, else the score alone. */
@Composable
private fun LinkVotes(link: SubjectLink, onVote: ((Boolean) -> Unit)?) {
    VotePill(
        score = link.score,
        myVote = when {
            link.myVote > 0 -> Vote.Up
            link.myVote < 0 -> Vote.Down
            else -> null
        },
        scoreDescription = stringResource(CoreRes.string.links_score, link.score),
        onVote = onVote?.let { vote -> { vote(it == Vote.Up) } },
        labels = VoteLabels(
            up = stringResource(CoreRes.string.links_vote_up),
            down = stringResource(CoreRes.string.links_vote_down),
        ),
    )
}

private const val WWW = "www."

/** The `minHeight` of 2.2's `item_group_action_row.xml`, a connected group's 56 dp row. */
private val ActionRowMinHeight = 56.dp

/** The 24 dp icons of 2.2's `item_group_action_row.xml`. */
private val ActionIconSize = 24.dp

private val previewScope = ResourceScope(RecordbookPreviewSamples.MATH_ID, RecordbookPreviewSamples.MATH, "2025-2")

private fun previewLink(
    id: String,
    category: LinkCategory,
    title: String?,
    url: String = "https://example.org/$id",
    score: Int = 0,
    myVote: Int = 0,
    mine: Boolean = false,
    visibility: LinkVisibility = LinkVisibility.ALL,
    audience: String? = null,
) = SubjectLink(
    id = id, scope = previewScope, category = category, url = url, title = title, visibility = visibility,
    flowId = null, audienceLabel = audience, status = SubjectLinkStatus.PUBLISHED, reviewNote = null, score = score,
    myVote = myVote, isMine = mine, reportedByMe = false, author = null,
    updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
)

@Composable
private fun LinksPreviewColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(bottom = ItmoTheme.spacing.screenMargin),
    ) { content() }
}

/**
 * The short list as `RecordbookSubjectScreen_sheet` shows it: the LMS page, a voted link with a long title, a link
 * the viewer voted down, an own link without a title and «Все ссылки»; then an own chat and a flow chat.
 */
@Preview(heightDp = 1100)
@Composable
private fun SubjectLinkItemPreview() = ItmoPreview {
    LinksPreviewColumn {
        SectionHeading(stringResource(CoreRes.string.links_title), spacing = SectionHeadingSpacing.First)
        SubjectLmsItem(SubjectHubItem.Lms("https://www.lms.itmo.ru/course/1", GroupPosition.First), onOpen = {})
        val links = listOf(
            previewLink("a", LinkCategory.RECORDINGS, "Записи лекций весны 2026 года с разбором задач", score = 5),
            previewLink("b", LinkCategory.NOTES, "Конспекты", score = -2, myVote = -1),
            previewLink("c", LinkCategory.SCORES, null, url = "https://docs.google.com/spreadsheets/d/x", mine = true),
        )
        links.forEach { link ->
            SubjectLinkItem(SubjectHubItem.Link(link, true, GroupPosition.Middle), {}, {}, { _, _ -> })
        }
        SubjectAllLinksRow(SubjectHubItem.AllLinks(7, GroupPosition.Last), onClick = {})
        SectionHeading(stringResource(CoreRes.string.links_chats))
        val own = previewLink(
            "chat-own", LinkCategory.CHAT, "Чат практики", url = "https://t.me/example", mine = true,
            visibility = LinkVisibility.FLOW, audience = "МАТ АН ПИИКТ 3.2",
        )
        val flow = previewLink("chat-flow", LinkCategory.CHAT, null, url = "https://vk.com/example")
        SubjectChatItem(SubjectHubItem.Chat(own, GroupPosition.First), {}, {})
        SubjectChatItem(SubjectHubItem.Chat(flow, GroupPosition.Last), {}, {})
    }
}

/** Without the connection the pills keep the score alone. */
@Preview(name = "read-only")
@Composable
private fun SubjectLinkItemReadOnlyPreview() = ItmoPreview {
    LinksPreviewColumn {
        val links = listOf(
            previewLink("a", LinkCategory.MATERIALS, "Материалы курса", score = 12),
            previewLink("b", LinkCategory.QUEUE, "Очередь на сдачу лабораторных", score = -1),
        )
        links.forEachIndexed { index, link ->
            SubjectLinkItem(SubjectHubItem.Link(link, false, GroupPosition.of(index, links.size)), {}, {}, { _, _ -> })
        }
    }
}

/** A subject without links offers to add the first one. */
@Preview
@Composable
private fun SubjectAddLinkRowPreview() = ItmoPreview {
    LinksPreviewColumn {
        SectionHeading(stringResource(CoreRes.string.links_title), spacing = SectionHeadingSpacing.First)
        SubjectAddLinkRow(SubjectHubItem.AddLink(GroupPosition.Single), onClick = {})
    }
}
