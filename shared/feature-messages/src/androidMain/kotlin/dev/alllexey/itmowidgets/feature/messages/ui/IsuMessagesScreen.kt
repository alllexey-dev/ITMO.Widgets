package dev.alllexey.itmowidgets.feature.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.presentation.GroupPosition
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.buttons.TonalButton
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateActionStyle
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SegmentedChoice
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.messages.domain.IsuFolder
import dev.alllexey.itmowidgets.feature.messages.domain.MessageBlock
import dev.alllexey.itmowidgets.feature.messages.presentation.IsuMessageDetail
import dev.alllexey.itmowidgets.feature.messages.presentation.IsuMessageRow
import dev.alllexey.itmowidgets.feature.messages.presentation.IsuMessagesList
import dev.alllexey.itmowidgets.feature.messages.presentation.IsuMessagesUiState
import dev.alllexey.itmowidgets.feature.messages.presentation.MoreState
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_attach_file
import dev.alllexey.itmowidgets.shared.designsystem.ic_download
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_login
import dev.alllexey.itmowidgets.shared.designsystem.ic_mail
import dev.alllexey.itmowidgets.shared.feature.messages.MessagesStrings
import org.jetbrains.compose.resources.painterResource

/** What the messages screen asks of its route. */
data class IsuMessagesActions(
    val onBack: () -> Unit = {},
    val onFolder: (IsuFolder) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    /** `Войти через ITMO.ID` when ISU wants the user: the sign-in that leaves the phone an ITMO.ID session. */
    val onSignIn: () -> Unit = {},
    val onMore: () -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onClose: () -> Unit = {},
    val onAttachment: (String) -> Unit = {},
    val onLink: (String) -> Unit = {},
)

/** Tags for host tests. */
object IsuMessagesTestTags {
    const val LIST = "isu_messages_list"
    const val ROW = "isu_messages_row"
    const val MORE = "isu_messages_more"
    const val DETAIL = "isu_messages_detail"
    const val ATTACHMENT = "isu_messages_attachment"
}

/**
 * Сообщения ИСУ (docs/features/isu-messages.md): the folder choice, the list by month with pull-to-refresh, the next
 * page loading at the end;
 * with [IsuMessagesUiState.opened] the one message instead, its body as plain styled text (links open outside the
 * app) and its attachments. Stateless; [IsuMessagesRoute] feeds it.
 */
@Composable
fun IsuMessagesScreen(
    state: IsuMessagesUiState,
    actions: IsuMessagesActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val opened = state.opened
    ItmoDetailScreen(
        title = opened?.subject ?: text(MessagesStrings.isu_messages_title),
        onBack = if (opened != null) actions.onClose else actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
        subtitle = if (opened == null) state.unreadText?.asString() else null,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (opened != null) {
                MessageDetail(opened, state.downloading, actions)
            } else {
                AppRefreshBox(refreshing = state.refreshing, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
                    MessageList(state, actions)
                }
            }
            ItmoSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun MessageList(state: IsuMessagesUiState, actions: IsuMessagesActions) {
    val folders = IsuFolder.entries
    LazyColumn(
        Modifier.fillMaxSize().testTag(IsuMessagesTestTags.LIST),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.Top,
    ) {
        item(key = "folders") {
            SegmentedChoice(
                options = listOf(text(MessagesStrings.isu_messages_inbox), text(MessagesStrings.isu_messages_archive)),
                selectedIndex = folders.indexOf(state.folder),
                onSelect = { actions.onFolder(folders[it]) },
                modifier = Modifier.padding(bottom = ItmoTheme.spacing.group),
            )
        }
        when (val list = state.list) {
            IsuMessagesList.Loading -> item(key = "loading") { Skeleton(SkeletonStyle.List, rows = SKELETON_ROWS) }
            is IsuMessagesList.Empty -> item(key = "empty") {
                ContentState(list.title.asString(), icon = painterResource(Res.drawable.ic_mail), description = list.text.asString())
            }
            is IsuMessagesList.Failed -> item(key = "failed") {
                ContentState(
                    list.title.asString(),
                    icon = painterResource(if (list.signIn) Res.drawable.ic_login else Res.drawable.ic_error),
                    description = list.text.asString(),
                    action = if (list.signIn) {
                        ContentStateAction(text(CoreStrings.auth_login_itmo_id), actions.onSignIn, ContentStateActionStyle.Filled)
                    } else {
                        ContentStateAction(text(CoreStrings.common_retry), actions.onRetry)
                    },
                )
            }
            is IsuMessagesList.Messages -> messages(list, actions)
        }
    }
}

/** A month heading over one connected group of its messages; the end of the list loads the next page by itself. */
private fun LazyListScope.messages(list: IsuMessagesList.Messages, actions: IsuMessagesActions) {
    list.sections.forEachIndexed { index, section ->
        section.title?.let { title ->
            item(key = "section:$index:$title") {
                SectionHeader(title, Modifier.padding(top = if (index == 0) 0.dp else ItmoTheme.spacing.group, bottom = ItmoTheme.spacing.content))
            }
        }
        itemsIndexed(section.rows, key = { _, row -> row.id }) { position, row ->
            MessageRow(row, GroupPosition.of(position, section.rows.size), onClick = { actions.onOpen(row.id) })
        }
    }
    if (list.more != MoreState.END) {
        item(key = "more") {
            Box(Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.group), contentAlignment = Alignment.Center) {
                if (list.more == MoreState.FAILED) {
                    TonalButton(onClick = actions.onMore, modifier = Modifier.testTag(IsuMessagesTestTags.MORE)) {
                        Text(text(MessagesStrings.isu_messages_more_failed))
                    }
                } else {
                    // Shown means scrolled to the end: the next page loads without a tap.
                    if (list.more == MoreState.AVAILABLE) LaunchedEffect(list.rows.size) { actions.onMore() }
                    ItmoActivityIndicator(Modifier.testTag(IsuMessagesTestTags.MORE))
                }
            }
        }
    }
}

/**
 * Sender, paperclip and date on the first line, the subject in up to two lines, two lines of the text. Unread: the
 * sender and the subject bold, the date in `primary` with a dot; read ones are in regular weight.
 */
@Composable
private fun MessageRow(row: IsuMessageRow, position: GroupPosition, onClick: () -> Unit) {
    val colors = ItmoTheme.colorScheme
    val type = ItmoTheme.typography
    val unread = text(MessagesStrings.isu_messages_unread)
    val files = if (row.attachments > 0) text(MessagesStrings.isu_messages_attachments_accessibility, row.attachments) else null
    Row(
        Modifier
            .fillMaxWidth()
            .connectedGroupItem(position)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(unread.takeIf { row.unread }, row.sender, row.subject, row.date, files, row.preview)
                    .joinToString(". ")
            }
            .testTag(IsuMessagesTestTags.ROW)
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group),
    ) {
        Avatar(row.sender, pictureUrl = null, size = AvatarSize)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
                Text(
                    row.sender,
                    Modifier.weight(1f),
                    style = type.titleSmall,
                    fontWeight = if (row.unread) FontWeight.Bold else FontWeight.Normal,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.attachments > 0) {
                    Icon(painterResource(Res.drawable.ic_attach_file), null, Modifier.size(SmallIcon), tint = colors.onSurfaceVariant)
                }
                Text(row.date, style = type.labelMedium, color = if (row.unread) colors.primary else colors.onSurfaceVariant)
                if (row.unread) Box(Modifier.size(UnreadDot).background(colors.primary, CircleShape))
            }
            Text(
                row.subject,
                style = type.bodyMedium,
                fontWeight = if (row.unread) FontWeight.SemiBold else null,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(row.preview, style = type.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MessageDetail(message: IsuMessageDetail, downloading: String?, actions: IsuMessagesActions) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(IsuMessagesTestTags.DETAIL),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        item(key = "sender") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.group)) {
                Avatar(message.sender, pictureUrl = null, size = AvatarSize)
                Column(Modifier.weight(1f)) {
                    Text(message.sender, style = ItmoTheme.typography.titleSmall, color = ItmoTheme.colorScheme.onSurface)
                    Text(message.date, style = ItmoTheme.typography.bodySmall, color = ItmoTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "body") {
            SelectionContainer {
                Column(
                    Modifier.padding(vertical = ItmoTheme.spacing.related),
                    verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
                ) {
                    message.body.blocks.forEach { block -> BodyBlock(block, actions.onLink) }
                }
            }
        }
        if (message.attachments.isNotEmpty()) {
            item(key = "files:title") { SectionHeader(text(MessagesStrings.isu_messages_files), count = "${message.attachments.size}") }
            item(key = "files") {
                ListGroup {
                    message.attachments.forEach { file ->
                        item {
                            ListRow(
                                file.name,
                                Modifier.testTag(IsuMessagesTestTags.ATTACHMENT),
                                subtitle = file.kind.asString(),
                                icon = painterResource(Res.drawable.ic_attach_file),
                                iconTone = Tone.Neutral,
                                accessory = {
                                    if (downloading == file.id) {
                                        ItmoActivityIndicator()
                                    } else {
                                        Icon(painterResource(Res.drawable.ic_download), null, tint = ItmoTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { actions.onAttachment(file.id) },
                            )
                        }
                    }
                }
            }
        }
        if (message.unread) {
            item(key = "note") { ListGroupNote(text(MessagesStrings.isu_messages_unread_note)) }
        }
    }
}

/** A paragraph, or a list item with its marker hanging before the text. */
@Composable
private fun BodyBlock(block: MessageBlock, onLink: (String) -> Unit) {
    val colors = ItmoTheme.colorScheme
    val links = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))
    val text: AnnotatedString = buildAnnotatedString {
        block.spans.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.underline) TextDecoration.Underline else null,
            )
            val link = span.link
            if (link == null) {
                withStyle(style) { append(span.text) }
            } else {
                withLink(LinkAnnotation.Url(link, links) { onLink(link) }) { withStyle(style) { append(span.text) } }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
        block.marker?.let { Text(it, Modifier.widthIn(min = MarkerWidth), style = ItmoTheme.typography.bodyLarge, color = colors.onSurfaceVariant) }
        Text(text, style = ItmoTheme.typography.bodyLarge, color = colors.onSurface)
    }
}

@Composable
private fun screenPadding() = PaddingValues(
    start = ItmoTheme.spacing.screenMargin,
    end = ItmoTheme.spacing.screenMargin,
    top = ItmoTheme.spacing.related,
    bottom = ItmoTheme.spacing.section,
)

@Composable
private fun text(key: String, vararg arguments: Any): String = UiText.Res(key, arguments.toList()).asString()

private const val SKELETON_ROWS = 5
private val AvatarSize = 40.dp
private val SmallIcon = 16.dp
private val UnreadDot = 8.dp
private val MarkerWidth = 20.dp
