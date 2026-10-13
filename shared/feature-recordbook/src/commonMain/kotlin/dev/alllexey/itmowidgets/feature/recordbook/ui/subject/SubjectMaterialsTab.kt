package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.icons.drawable
import dev.alllexey.itmowidgets.designsystem.kit.GateCard
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.kit.VotePill
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLinkRow
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectMaterials
import dev.alllexey.itmowidgets.feature.recordbook.ui.kit
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.compose.strings.links_vote_down
import dev.alllexey.itmowidgets.shared.compose.strings.links_vote_up
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.links_all_count
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_connect
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_link_add
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_link_mine
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_link_more
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_materials_chats
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_materials_empty_text
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_materials_empty_title
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_materials_off_text
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_materials_off_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** `Материалы`: chats, links by category with their votes, own links with their review state; a gate without the connection. */
internal fun LazyListScope.materialsTab(materials: SubjectMaterials, actions: RecordbookSubjectActions) {
    when (materials) {
        SubjectMaterials.Gate -> item(key = "materials-gate") {
            GateCard(
                title = stringResource(Res.string.subject_materials_off_title),
                reason = stringResource(Res.string.subject_materials_off_text),
                fix = stringResource(Res.string.subject_connect),
                onFix = actions.onConnect,
                modifier = Modifier.testTag(SubjectTestTags.MATERIALS_GATE),
                icon = painterResource(KitRes.drawable.ic_cloud_off),
            )
        }
        SubjectMaterials.Loading -> item(key = "materials-loading") { ContentStateLoading(size = ContentStateSize.Compact) }
        is SubjectMaterials.Failed -> item(key = "materials-error") {
            ContentState(
                title = stringResource(CoreRes.string.common_load_error_title),
                size = ContentStateSize.Compact,
                description = materials.error.toUiText().asString(),
                action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRefresh),
            )
        }
        is SubjectMaterials.Empty -> item(key = "materials-empty") {
            ContentState(
                title = stringResource(Res.string.subject_materials_empty_title),
                size = ContentStateSize.Compact,
                icon = painterResource(KitRes.drawable.ic_link),
                description = stringResource(Res.string.subject_materials_empty_text),
                action = ContentStateAction(stringResource(Res.string.subject_link_add), { actions.onAddLink(materials.args) }),
            )
        }
        is SubjectMaterials.Content -> {
            if (materials.chats.isNotEmpty()) {
                item(key = "chats-title") { SectionHeader(stringResource(Res.string.subject_materials_chats)) }
                item(key = "chats") { LinkGroup(materials.chats, materials.args, actions, chats = true) }
            }
            materials.sections.forEach { section ->
                item(key = "links-title-${section.title}") { SectionHeader(section.title.asString()) }
                item(key = "links-${section.title}") { LinkGroup(section.rows, materials.args, actions, chats = false) }
            }
            if (materials.previous > 0) {
                item(key = "links-previous") {
                    ListGroup {
                        item {
                            ListRow(
                                title = stringResource(Res.string.links_all_count, materials.previous),
                                chevron = true,
                                onClick = { actions.onAllLinks(materials.args) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkGroup(rows: List<SubjectLinkRow>, args: SubjectLinksArgs, actions: RecordbookSubjectActions, chats: Boolean) {
    ListGroup { rows.forEach { row -> item { LinkRow(row, args, actions, chats) } } }
}

/** A link: tap opens it, a long press its actions; the votes on the right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinkRow(row: SubjectLinkRow, args: SubjectLinksArgs, actions: RecordbookSubjectActions, chat: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClickLabel = stringResource(Res.string.subject_link_more),
                onLongClick = { actions.onLinkActions(args, row.id) },
                onClick = { actions.onOpenLink(row.url) },
            )
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .padding(start = ItmoTheme.spacing.cardPadding, end = ItmoTheme.spacing.content)
            .padding(vertical = ItmoTheme.spacing.content)
            .testTag(SubjectTestTags.link(row.id)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        IconTile(painterResource(row.icon.drawable), if (chat) Tone.Primary else Tone.Neutral)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
            Text(row.title, style = ItmoTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                row.line.asString(),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.own) {
                Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
                    StatusPill(stringResource(Res.string.subject_link_mine))
                    row.status?.let { StatusPill(it.asString(), tone = row.statusTone.kit()) }
                }
            }
        }
        row.score?.let { score ->
            VotePill(
                score = score,
                myVote = row.myVote,
                upLabel = stringResource(CoreRes.string.links_vote_up),
                downLabel = stringResource(CoreRes.string.links_vote_down),
                onUp = { actions.onVote(row.id, true) },
                onDown = { actions.onVote(row.id, false) },
                enabled = row.canVote,
            )
        }
    }
}
