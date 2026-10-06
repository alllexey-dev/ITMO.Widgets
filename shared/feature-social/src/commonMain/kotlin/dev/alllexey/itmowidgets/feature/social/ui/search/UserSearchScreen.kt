package dev.alllexey.itmowidgets.feature.social.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.presentation.UserSearchUiState
import dev.alllexey.itmowidgets.feature.social.ui.list.UserList
import dev.alllexey.itmowidgets.feature.social.ui.list.UserListTestTags
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_search
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.user_search_clear
import dev.alllexey.itmowidgets.shared.feature.social.user_search_empty_title
import dev.alllexey.itmowidgets.shared.feature.social.user_search_hint
import dev.alllexey.itmowidgets.shared.feature.social.user_search_idle_description
import dev.alllexey.itmowidgets.shared.feature.social.user_search_idle_title
import dev.alllexey.itmowidgets.shared.feature.social.user_search_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [UserSearchScreen]; the list and its rows carry [UserListTestTags]. */
object UserSearchTestTags {
    const val FIELD = "user_search_field"
    const val CLEAR = "user_search_clear"
    const val LOADING = "user_search_loading"
    const val LOADING_MORE = "user_search_loading_more"

    /** The idle, empty or error state in the list's place. */
    const val STATE = "user_search_state"
}

/**
 * People search: the [query] field under the title, then the results of [state]. Content lists `В ITMO.Widgets`
 * (the relationship action: add, cancel, accept) over `Остальные` (`Пригласить`) and ends with `Показать ещё` while
 * Backend has another page; the next page in flight shows a bar at the bottom instead. Every row opens the person's
 * profile. Nothing typed is the idle hint, nobody found the empty state, a failed first page a retry; the first page
 * in flight shows list placeholders. A row in flight keeps its buttons inert; failed actions go to
 * [snackbarHostState].
 *
 * With [requestFocus] the field takes focus once, when the screen first opens (the route asks for it; previews keep
 * the resting field without a blinking cursor). It offers a clear button while it holds text. The query debounce
 * lives in the ViewModel. Stateless; [UserSearchRoute] feeds it.
 */
@Composable
fun UserSearchScreen(
    query: String,
    state: UserSearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onAction: (UserRowUi, UserAction) -> Unit,
    onLoadMore: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(Res.string.user_search_title),
                navigation = {
                    AppTopBarAction(
                        painterResource(KitRes.drawable.ic_arrow_back),
                        stringResource(CoreRes.string.common_back),
                        onClick = onBack,
                    )
                },
            )
            SearchField(
                query,
                onQueryChange,
                requestFocus,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ItmoTheme.spacing.group)
                    .padding(top = ItmoTheme.spacing.compact),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SearchBody(state, onRetry, onAction, onLoadMore, onOpenProfile)
                if ((state as? UserSearchUiState.Content)?.loadingMore == true) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .testTag(UserSearchTestTags.LOADING_MORE),
                    )
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The dense outlined `TextInputLayout` of `fragment_user_search.xml`: a search icon, the hint as the label, a clear
 * button while there is text, 48 dp high. Words start capitalised, as in `textPersonName|textCapWords`.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, requestFocus: Boolean, modifier: Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focus = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(requestFocus) {
        if (requestFocus && !focusedOnce) {
            focus.requestFocus()
            focusedOnce = true
        }
    }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .focusRequester(focus)
            .testTag(UserSearchTestTags.FIELD),
        textStyle = ItmoTheme.typography.bodyLarge.copy(color = ItmoTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(ItmoTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = query,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = { Text(stringResource(Res.string.user_search_hint)) },
                leadingIcon = { Icon(painterResource(KitRes.drawable.ic_search), contentDescription = null) },
                trailingIcon = if (query.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { onQueryChange("") }, Modifier.testTag(UserSearchTestTags.CLEAR)) {
                            Icon(ClearTextIcon, stringResource(Res.string.user_search_clear))
                        }
                    }
                },
                contentPadding = OutlinedTextFieldDefaults.contentPaddingWithLabel(
                    top = ItmoTheme.spacing.content,
                    bottom = ItmoTheme.spacing.content,
                ),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                    )
                },
            )
        },
    )
}

@Composable
private fun SearchBody(
    state: UserSearchUiState,
    onRetry: () -> Unit,
    onAction: (UserRowUi, UserAction) -> Unit,
    onLoadMore: () -> Unit,
    onOpenProfile: (Int) -> Unit,
) {
    when (state) {
        UserSearchUiState.Idle -> SearchState(
            painterResource(KitRes.drawable.ic_search),
            stringResource(Res.string.user_search_idle_title),
            stringResource(Res.string.user_search_idle_description),
        )
        UserSearchUiState.Loading ->
            Skeleton(SkeletonStyle.List, Modifier.fillMaxSize().testTag(UserSearchTestTags.LOADING))
        UserSearchUiState.Empty -> SearchState(
            painterResource(KitRes.drawable.ic_person),
            stringResource(Res.string.user_search_empty_title),
        )
        is UserSearchUiState.Error -> SearchState(
            painterResource(KitRes.drawable.ic_error),
            stringResource(CoreRes.string.common_load_error_title),
            stringResource(state.error.textResource()),
            ContentStateAction(stringResource(CoreRes.string.common_retry), onRetry),
        )
        is UserSearchUiState.Content -> UserList(
            state.items,
            onOpen = { onOpenProfile(it.isu) },
            modifier = Modifier.fillMaxSize().padding(top = ItmoTheme.spacing.compact),
            onAction = onAction,
            onLoadMore = onLoadMore,
        )
    }
}

@Composable
private fun SearchState(
    icon: Painter,
    title: String,
    description: String? = null,
    action: ContentStateAction? = null,
) {
    ContentState(
        title = title,
        modifier = Modifier.fillMaxSize().testTag(UserSearchTestTags.STATE),
        icon = icon,
        description = description,
        action = action,
    )
}

/** Material's `mtrl_ic_cancel`, the clear-text end icon of `TextInputLayout`; the kit has no filled cancel symbol. */
private val ClearTextIcon: ImageVector by lazy {
    ImageVector.Builder("ClearText", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.47f, 2f, 2f, 6.47f, 2f, 12f)
            reflectiveCurveToRelative(4.47f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.47f, 10f, -10f)
            reflectiveCurveTo(17.53f, 2f, 12f, 2f)
            close()
            moveTo(17f, 15.59f)
            lineTo(15.59f, 17f)
            lineTo(12f, 13.41f)
            lineTo(8.41f, 17f)
            lineTo(7f, 15.59f)
            lineTo(10.59f, 12f)
            lineTo(7f, 8.41f)
            lineTo(8.41f, 7f)
            lineTo(12f, 10.59f)
            lineTo(15.59f, 7f)
            lineTo(17f, 8.41f)
            lineTo(13.41f, 12f)
            close()
        }
    }.build()
}
