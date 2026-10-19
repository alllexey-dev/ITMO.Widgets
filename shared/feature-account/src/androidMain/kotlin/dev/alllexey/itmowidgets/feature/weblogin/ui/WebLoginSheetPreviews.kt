package dev.alllexey.itmowidgets.feature.weblogin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginUiState

/*
 * The harness names a baseline `<function>_<@Preview name>`, and the baselines are named
 * `WebLoginSheetContent_<state>`. Each state therefore is a function called `WebLoginSheetContent` in a holder class
 * of its own. The sheet fits its content, so every preview wraps it on the sheet's colour under rounded top corners,
 * as the shell's sheet opens it.
 */

/** An empty field: `Продолжить` disabled. */
internal class WebLoginSheetInputPreview {
    @Preview(name = "input")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.input)
}

/** A malformed code: the error under the field. */
internal class WebLoginSheetInputErrorPreview {
    @Preview(name = "input-error")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.invalid)
}

/** A well-formed code Backend does not know: fixed in place. */
internal class WebLoginSheetNotFoundPreview {
    @Preview(name = "not-found")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.notFound)
}

/** The Play services scanner could not start: typing still works. */
internal class WebLoginSheetScannerPreview {
    @Preview(name = "scanner-unavailable")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.scannerUnavailable)
}

/** The code is checked: everything disabled, the progress inside `Продолжить`. */
internal class WebLoginSheetCheckingPreview {
    @Preview(name = "checking")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.checking)
}

/** Chrome on macOS waiting for `Войти`. */
internal class WebLoginSheetConfirmPreview {
    @Preview(name = "confirm")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.confirm)
}

/** The longest browser name. */
internal class WebLoginSheetLongAgentPreview {
    @Preview(name = "long-agent")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.longAgent)
}

/** The approval runs: both buttons disabled, the progress inside `Войти`. */
internal class WebLoginSheetApprovingPreview {
    @Preview(name = "approving")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.approving)
}

internal class WebLoginSheetDonePreview {
    @Preview(name = "done")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.done)
}

/** A network error: `Повторить` checks the same code again. */
internal class WebLoginSheetErrorPreview {
    @Preview(name = "error")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.network)
}

/** The approval found the code used or expired: `Повторить` opens an empty field. */
internal class WebLoginSheetExpiredPreview {
    @Preview(name = "expired")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.expired)
}

/** A new agent: its permissions as switches, the shared session and the verification code. */
internal class WebLoginSheetAgentPreview {
    @Preview(name = "agent")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.agent)
}

/** The longest name an agent may choose, one permission off. */
internal class WebLoginSheetAgentLongNamePreview {
    @Preview(name = "agent-long-name")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.agentLongName)
}

/** A connected agent asking for a new session: permissions shown, not switched. */
internal class WebLoginSheetAgentSessionPreview {
    @Preview(name = "agent-session")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.agentSession)
}

/** The phone takes a new session and hands out the previous one. */
internal class WebLoginSheetRotatingPreview {
    @Preview(name = "rotating")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginUiState.Rotating)
}

internal class WebLoginSheetSendingPreview {
    @Preview(name = "sending")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginUiState.Sending)
}

internal class WebLoginSheetPairedPreview {
    @Preview(name = "paired")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.paired)
}

/** The code of one QR with the key of another: no retry. */
internal class WebLoginSheetMismatchPreview {
    @Preview(name = "mismatch")
    @Composable
    fun WebLoginSheetContent() = SheetPreview(WebLoginSamples.mismatch)
}

@Composable
private fun SheetPreview(state: WebLoginUiState) = ItmoPreview {
    val shape = ItmoTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLow),
    ) {
        WebLoginSheetContent(state, WebLoginActions())
    }
}
