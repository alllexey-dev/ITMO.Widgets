package dev.alllexey.itmowidgets.feature.messages.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.feature.messages.presentation.IsuMessagesSamples

// The messages screen in its states; the names are `<Screen>_<state>`.

private const val SCREEN_HEIGHT = 800

internal class IsuMessagesInboxPreview {
    @Preview(name = "inbox", heightDp = SCREEN_HEIGHT)
    @Composable
    fun IsuMessages() = ItmoPreview { IsuMessagesScreen(IsuMessagesSamples.Inbox, IsuMessagesActions()) }
}

internal class IsuMessagesOpenedPreview {
    @Preview(name = "opened", heightDp = SCREEN_HEIGHT)
    @Composable
    fun IsuMessages() = ItmoPreview { IsuMessagesScreen(IsuMessagesSamples.Opened, IsuMessagesActions()) }
}

internal class IsuMessagesLoadingPreview {
    @Preview(name = "loading", heightDp = SCREEN_HEIGHT)
    @Composable
    fun IsuMessages() = ItmoPreview { IsuMessagesScreen(IsuMessagesSamples.Loading, IsuMessagesActions()) }
}

internal class IsuMessagesEmptyArchivePreview {
    @Preview(name = "empty-archive", heightDp = SCREEN_HEIGHT)
    @Composable
    fun IsuMessages() = ItmoPreview { IsuMessagesScreen(IsuMessagesSamples.EmptyArchive, IsuMessagesActions()) }
}

internal class IsuMessagesSignInPreview {
    @Preview(name = "sign-in", heightDp = SCREEN_HEIGHT)
    @Composable
    fun IsuMessages() = ItmoPreview { IsuMessagesScreen(IsuMessagesSamples.SignIn, IsuMessagesActions()) }
}

internal class IsuMessagesOfflinePreview {
    @Preview(name = "offline", heightDp = SCREEN_HEIGHT)
    @Composable
    fun IsuMessages() = ItmoPreview { IsuMessagesScreen(IsuMessagesSamples.Offline, IsuMessagesActions()) }
}
