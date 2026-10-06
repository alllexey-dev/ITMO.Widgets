package dev.alllexey.itmowidgets.feature.social.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.social.ui.list.preview.UserListPreviewData

/**
 * Headers, accept and reject, a cancel in flight, add, a closed invite row and load-more. Tall enough to hold every
 * row at font 1.3: a list that scrolls in the capture fails ATF's speakable-text check on its scroll container.
 */
@Preview(heightDp = 1000)
@Composable
private fun UserListPreview() = ItmoPreview {
    UserList(UserListPreviewData.Mixed, onOpen = {})
}
