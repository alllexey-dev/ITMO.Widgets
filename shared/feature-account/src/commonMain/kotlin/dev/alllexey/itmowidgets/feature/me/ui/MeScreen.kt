package dev.alllexey.itmowidgets.feature.me.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsGroup
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.me.presentation.MeFriendsSummary
import dev.alllexey.itmowidgets.feature.me.presentation.MeUiState
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.core.debug_tools_title
import dev.alllexey.itmowidgets.shared.core.me_group_app
import dev.alllexey.itmowidgets.shared.core.settings_title
import dev.alllexey.itmowidgets.shared.core.share_action
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_brand_github
import dev.alllexey.itmowidgets.shared.designsystem.ic_brand_telegram
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_code_blocks
import dev.alllexey.itmowidgets.shared.designsystem.ic_computer
import dev.alllexey.itmowidgets.shared.designsystem.ic_globe
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import dev.alllexey.itmowidgets.shared.designsystem.ic_logout
import dev.alllexey.itmowidgets.shared.designsystem.ic_search
import dev.alllexey.itmowidgets.shared.designsystem.ic_settings
import dev.alllexey.itmowidgets.shared.designsystem.ic_share
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.me_find_people_description
import dev.alllexey.itmowidgets.shared.feature.account.me_find_people_title
import dev.alllexey.itmowidgets.shared.feature.account.me_friends_count
import dev.alllexey.itmowidgets.shared.feature.account.me_friends_error
import dev.alllexey.itmowidgets.shared.feature.account.me_friends_loading
import dev.alllexey.itmowidgets.shared.feature.account.me_friends_none
import dev.alllexey.itmowidgets.shared.feature.account.me_friends_title
import dev.alllexey.itmowidgets.shared.feature.account.me_github
import dev.alllexey.itmowidgets.shared.feature.account.me_github_accessibility
import dev.alllexey.itmowidgets.shared.feature.account.me_group_format
import dev.alllexey.itmowidgets.shared.feature.account.me_group_social
import dev.alllexey.itmowidgets.shared.feature.account.me_isu
import dev.alllexey.itmowidgets.shared.feature.account.me_privacy_description
import dev.alllexey.itmowidgets.shared.feature.account.me_privacy_title
import dev.alllexey.itmowidgets.shared.feature.account.me_requests_badge
import dev.alllexey.itmowidgets.shared.feature.account.me_requests_badge_accessibility
import dev.alllexey.itmowidgets.shared.feature.account.me_services_disabled_description
import dev.alllexey.itmowidgets.shared.feature.account.me_services_disabled_title
import dev.alllexey.itmowidgets.shared.feature.account.me_sign_out
import dev.alllexey.itmowidgets.shared.feature.account.me_sign_out_confirm_message
import dev.alllexey.itmowidgets.shared.feature.account.me_sign_out_confirm_title
import dev.alllexey.itmowidgets.shared.feature.account.me_telegram
import dev.alllexey.itmowidgets.shared.feature.account.me_telegram_accessibility
import dev.alllexey.itmowidgets.shared.feature.account.me_unknown_user
import dev.alllexey.itmowidgets.shared.feature.account.me_web_login_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The project pages under the Me tab's groups; the host knows their addresses. */
enum class MeProjectLink { GITHUB, TELEGRAM }

/**
 * What the Me tab asks its host to do. Navigation, sharing and links are platform work; [onOpenProjectLink] answers
 * whether any app opened the page, and the route says so when none did.
 */
data class MeActions(
    val onOpenFriends: () -> Unit = {},
    val onFindPeople: () -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    /** The services-off row: the settings, where the connection is switched on. */
    val onOpenServices: () -> Unit = {},
    val onOpenWebLogin: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenDebugTools: () -> Unit = {},
    /** Shares the profile under the [name] the header shows. */
    val onShareProfile: (name: String, isu: Int) -> Unit = { _, _ -> },
    val onOpenProjectLink: (MeProjectLink) -> Boolean = { true },
    val onSignOut: () -> Unit = {},
)

/** Tags named after the View ids of the XML screen they replace, for host and instrumented tests. */
object MeTestTags {
    const val ROOT = "main"
    const val PROFILE_NAME = "profile_name"
    const val PROFILE_GROUP = "profile_group"
    const val PROFILE_META = "profile_meta"
    const val SHARE = "profile_share_button"
    const val FRIENDS_ROW = "friends_row"
    const val REQUESTS_BADGE = "requests_badge"
    const val FIND_PEOPLE_ROW = "find_people_row"
    const val PRIVACY_ROW = "privacy_row"
    const val SERVICES_DISABLED_ROW = "services_disabled_row"
    const val WEB_LOGIN_ROW = "web_login_row"
    const val SETTINGS_ROW = "settings_row"
    const val DEBUG_TOOLS_ROW = "debug_tools_row"
    const val GITHUB = "github_button"
    const val TELEGRAM = "telegram_button"
    const val SIGN_OUT = "sign_out_row"
}

/**
 * The Me tab: the own identity with a share button, the friends group (or the services-off row), the application
 * group, the project links and the sign-out button behind a confirmation. [showDebugTools] adds the developer tools
 * row; the Android host passes its debug flag. Stateless; [MeRoute] feeds it.
 */
@Composable
fun MeScreen(
    state: MeUiState,
    showDebugTools: Boolean,
    actions: MeActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(
            Modifier
                .fillMaxSize()
                .testTag(MeTestTags.ROOT)
                .verticalScroll(rememberScrollState())
                .padding(start = ItmoTheme.spacing.screenMargin, end = ItmoTheme.spacing.screenMargin)
                .padding(bottom = ItmoTheme.spacing.group),
        ) {
            ProfileHeader(state, actions)
            SocialGroup(state.friends, actions)
            AppGroup(
                state.webLoginAvailable,
                showDebugTools,
                actions,
                Modifier.padding(top = ItmoTheme.spacing.group),
            )
            ProjectLinks(actions)
            SignOutButton(
                enabled = !state.signOutInProgress,
                onClick = { confirmingSignOut = true },
            )
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    if (confirmingSignOut) {
        ConfirmDialog(
            title = stringResource(Res.string.me_sign_out_confirm_title),
            text = stringResource(Res.string.me_sign_out_confirm_message),
            confirmLabel = stringResource(Res.string.me_sign_out),
            dismissLabel = stringResource(CoreRes.string.common_cancel),
            onConfirm = {
                confirmingSignOut = false
                actions.onSignOut()
            },
            onDismiss = { confirmingSignOut = false },
            destructive = true,
        )
    }
}

/** Avatar and name from the ID token, else from Backend; the group from Backend; the ISU from either. */
@Composable
private fun ProfileHeader(state: MeUiState, actions: MeActions) {
    val user = state.user
    val backendUser = state.backendUser
    val backendName = backendUser?.name?.takeIf { it.isNotBlank() }
    val knownName = user?.name ?: backendName
    val name = knownName ?: stringResource(Res.string.me_unknown_user)
    val group = backendUser?.primaryGroup()
    val isu = user?.isu ?: backendUser?.isu
    Row(
        Modifier.fillMaxWidth().padding(vertical = ItmoTheme.spacing.group),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(knownName, user?.pictureUrl ?: backendUser?.pictureUrl, size = AvatarSize)
        Column(Modifier.weight(1f).padding(start = ItmoTheme.spacing.group)) {
            Text(
                name,
                Modifier.testTag(MeTestTags.PROFILE_NAME),
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.titleMedium.copy(
                    hyphens = Hyphens.Auto,
                    lineBreak = LineBreak.Paragraph,
                ),
            )
            if (group != null) {
                HeaderLine(
                    stringResource(Res.string.me_group_format, group.name, group.course, group.facultyShortName),
                    MeTestTags.PROFILE_GROUP,
                )
            }
            if (isu != null) HeaderLine(stringResource(Res.string.me_isu, isu), MeTestTags.PROFILE_META)
        }
        if (isu != null && isu > 0) {
            IconButton(
                onClick = { actions.onShareProfile(name, isu) },
                modifier = Modifier.padding(start = ItmoTheme.spacing.compact).testTag(MeTestTags.SHARE),
            ) {
                Icon(
                    painterResource(KitRes.drawable.ic_share),
                    contentDescription = stringResource(CoreRes.string.share_action),
                    tint = ItmoTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HeaderLine(text: String, tag: String) {
    Text(
        text,
        Modifier.padding(top = ItmoTheme.spacing.related).testTag(tag),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodyMedium,
    )
}

@Composable
private fun SocialGroup(friends: MeFriendsSummary, actions: MeActions) {
    val friendsTitle = stringResource(Res.string.me_friends_title)
    val friendsDescription = when (friends) {
        MeFriendsSummary.Loading -> stringResource(Res.string.me_friends_loading)
        MeFriendsSummary.Error -> stringResource(Res.string.me_friends_error)
        is MeFriendsSummary.Content -> if (friends.friends == 0) {
            stringResource(Res.string.me_friends_none)
        } else {
            stringResource(Res.string.me_friends_count, friends.friends)
        }
        MeFriendsSummary.Disabled -> ""
    }
    val incoming = (friends as? MeFriendsSummary.Content)?.incomingRequests ?: 0
    val findPeople = rowTexts(Res.string.me_find_people_title, Res.string.me_find_people_description)
    val privacy = rowTexts(Res.string.me_privacy_title, Res.string.me_privacy_description)
    val servicesOff = rowTexts(Res.string.me_services_disabled_title, Res.string.me_services_disabled_description)
    val groupIcon = painterResource(KitRes.drawable.ic_group)
    val searchIcon = painterResource(KitRes.drawable.ic_search)
    val lockIcon = painterResource(KitRes.drawable.ic_lock)
    val globeIcon = painterResource(KitRes.drawable.ic_globe)
    SettingsGroup(title = stringResource(Res.string.me_group_social)) {
        if (friends == MeFriendsSummary.Disabled) {
            row(MeTestTags.SERVICES_DISABLED_ROW) {
                MeRow(
                    globeIcon,
                    servicesOff.first,
                    MeTestTags.SERVICES_DISABLED_ROW,
                    actions.onOpenServices,
                    servicesOff.second,
                )
            }
        } else {
            row(MeTestTags.FRIENDS_ROW) {
                MeRow(groupIcon, friendsTitle, MeTestTags.FRIENDS_ROW, actions.onOpenFriends, friendsDescription) {
                    if (incoming > 0) RequestsBadge(incoming)
                }
            }
            row(MeTestTags.FIND_PEOPLE_ROW) {
                MeRow(searchIcon, findPeople.first, MeTestTags.FIND_PEOPLE_ROW, actions.onFindPeople, findPeople.second)
            }
            row(MeTestTags.PRIVACY_ROW) {
                MeRow(lockIcon, privacy.first, MeTestTags.PRIVACY_ROW, actions.onOpenPrivacy, privacy.second)
            }
        }
    }
}

@Composable
private fun AppGroup(webLoginAvailable: Boolean, showDebugTools: Boolean, actions: MeActions, modifier: Modifier) {
    val webLogin = stringResource(Res.string.me_web_login_title)
    val settings = stringResource(CoreRes.string.settings_title)
    val debugTools = stringResource(CoreRes.string.debug_tools_title)
    val computerIcon = painterResource(KitRes.drawable.ic_computer)
    val settingsIcon = painterResource(KitRes.drawable.ic_settings)
    val codeIcon = painterResource(KitRes.drawable.ic_code_blocks)
    SettingsGroup(modifier, title = stringResource(CoreRes.string.me_group_app)) {
        if (webLoginAvailable) {
            row(MeTestTags.WEB_LOGIN_ROW) {
                MeRow(computerIcon, webLogin, MeTestTags.WEB_LOGIN_ROW, actions.onOpenWebLogin)
            }
        }
        row(MeTestTags.SETTINGS_ROW) { MeRow(settingsIcon, settings, MeTestTags.SETTINGS_ROW, actions.onOpenSettings) }
        if (showDebugTools) {
            row(MeTestTags.DEBUG_TOOLS_ROW) {
                MeRow(codeIcon, debugTools, MeTestTags.DEBUG_TOOLS_ROW, actions.onOpenDebugTools)
            }
        }
    }
}

@Composable
private fun rowTexts(title: StringResource, description: StringResource): Pair<String, String> =
    stringResource(title) to stringResource(description)

/**
 * A row of the Me groups (`Widget.ItmoWidgets.CompactSettingsRow` with a leading icon): the decorative [icon], the
 * [title] in `bodyLarge` with an optional [description], an optional [badge] and a chevron. The whole row is one
 * target and one screen-reader stop.
 */
@Composable
private fun MeRow(
    icon: Painter,
    title: String,
    tag: String,
    onClick: () -> Unit,
    description: String? = null,
    badge: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = ItmoTheme.spacing.group, vertical = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(end = ItmoTheme.spacing.group).size(IconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
        // A described row keeps the compact text margin; a single-line row the settings one.
        val textEnd = if (description == null) ItmoTheme.spacing.group else ItmoTheme.spacing.content
        Column(Modifier.weight(1f).padding(end = textEnd)) {
            Text(title, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    Modifier.padding(top = ItmoTheme.spacing.related),
                    color = ItmoTheme.colorScheme.onSurfaceVariant,
                    style = ItmoTheme.typography.bodyMedium,
                )
            }
        }
        badge()
        Icon(
            painterResource(KitRes.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The incoming requests on the friends row (`bg_friend_selection_badge`), read as its accessibility text. */
@Composable
private fun RequestsBadge(count: Int) {
    val description = stringResource(Res.string.me_requests_badge_accessibility, count)
    val fill = ItmoTheme.colorScheme.primary
    val ring = ItmoTheme.colorScheme.surface
    Text(
        stringResource(Res.string.me_requests_badge, count),
        Modifier
            .padding(end = ItmoTheme.spacing.compact)
            // Read as the description alone, never as the bare number.
            .clearAndSetSemantics {
                testTag = MeTestTags.REQUESTS_BADGE
                contentDescription = description
            }
            .widthIn(min = BadgeMinWidth)
            .drawBehind {
                val stroke = BadgeStroke.toPx()
                drawOval(ring)
                drawOval(fill, Offset(stroke, stroke), Size(size.width - 2 * stroke, size.height - 2 * stroke))
            }
            .padding(horizontal = ItmoTheme.spacing.compact, vertical = BadgeVerticalPadding),
        color = ItmoTheme.colorScheme.onPrimary,
        style = ItmoTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
    )
}

/** Two tonal buttons, centred under the groups. */
@Composable
private fun ProjectLinks(actions: MeActions) {
    Row(
        Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.group),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact, Alignment.CenterHorizontally),
    ) {
        ProjectLinkButton(
            label = stringResource(Res.string.me_github),
            description = stringResource(Res.string.me_github_accessibility),
            icon = painterResource(KitRes.drawable.ic_brand_github),
            tag = MeTestTags.GITHUB,
            onClick = { actions.onOpenProjectLink(MeProjectLink.GITHUB) },
        )
        ProjectLinkButton(
            label = stringResource(Res.string.me_telegram),
            description = stringResource(Res.string.me_telegram_accessibility),
            icon = painterResource(KitRes.drawable.ic_brand_telegram),
            tag = MeTestTags.TELEGRAM,
            onClick = { actions.onOpenProjectLink(MeProjectLink.TELEGRAM) },
        )
    }
}

/** `Widget.ItmoWidgets.ProjectLink`: a 40 dp tonal pill in a 48 dp target, an 18 dp icon, the label in `labelLarge`. */
@Composable
private fun ProjectLinkButton(label: String, description: String, icon: Painter, tag: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag).semantics { contentDescription = description },
        contentPadding = PaddingValues(start = ItmoTheme.spacing.group, end = ItmoTheme.spacing.summaryPadding),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(label, Modifier.clearAndSetSemantics {}, style = ItmoTheme.typography.labelLarge)
    }
}

/** Sign-out: a text button in `onSurfaceVariant` at the start, inert while a sign-out runs. */
@Composable
private fun SignOutButton(enabled: Boolean, onClick: () -> Unit) {
    val color = ItmoTheme.colorScheme.onSurfaceVariant
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(top = ItmoTheme.spacing.content)
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .testTag(MeTestTags.SIGN_OUT),
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Icon(
            painterResource(KitRes.drawable.ic_logout),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.me_sign_out))
    }
}

/** `fragment_me.xml`'s 72 dp avatar. */
private val AvatarSize = 72.dp

/** The 24 dp icons and chevrons of the XML rows. */
private val IconSize = 24.dp

/** `bg_friend_selection_badge`'s 2 dp ring in the surface colour. */
private val BadgeStroke = 2.dp

/** The badge's `android:minWidth`. */
private val BadgeMinWidth = 24.dp

/** The badge's `android:paddingVertical`. */
private val BadgeVerticalPadding = 2.dp
