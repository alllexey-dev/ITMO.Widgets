package dev.alllexey.itmowidgets.feature.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LA-1c recorded the XML reference as
 * `MyItmoWebScreen_error`. Each state therefore is a function called `MyItmoWebScreen` in a holder class of its own.
 */

@Composable
private fun MyItmoWebPreview(state: MyItmoWebState) = ItmoPreview {
    MyItmoWebScreen(
        state = state,
        host = "my.itmo.ru",
        onClose = {},
        onReload = {},
        onOpenExternal = {},
        onRetry = {},
        browser = { modifier -> BrowserPlaceholder(modifier) },
    )
}

/** Stands in for the platform browser, which does not render under Robolectric: a tinted area. */
@Composable
private fun BrowserPlaceholder(modifier: Modifier) {
    Box(modifier.background(ItmoTheme.colorScheme.surfaceContainerLow))
}

internal class MyItmoWebScreenLoadingPreview {
    @Preview(name = "loading")
    @Composable
    fun MyItmoWebScreen() = MyItmoWebPreview(MyItmoWebState.Loading)
}

internal class MyItmoWebScreenShownPreview {
    @Preview(name = "shown")
    @Composable
    fun MyItmoWebScreen() = MyItmoWebPreview(MyItmoWebState.Shown)
}

internal class MyItmoWebScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun MyItmoWebScreen() = MyItmoWebPreview(MyItmoWebState.Failed)
}
