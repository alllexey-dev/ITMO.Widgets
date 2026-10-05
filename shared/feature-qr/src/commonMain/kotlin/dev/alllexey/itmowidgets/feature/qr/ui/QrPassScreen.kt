package dev.alllexey.itmowidgets.feature.qr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.textResource
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarAction
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeUiState
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeImage
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.qrColors
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.common_load_error_title
import dev.alllexey.itmowidgets.shared.feature.qr.Res
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_description
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_empty_description
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_empty_title
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_image_description
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_refresh
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_title
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code
import dev.alllexey.itmowidgets.shared.designsystem.ic_refresh
import kotlin.math.min
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [QrPassScreen], read by host tests and the instrumented home-to-pass flow. */
object QrPassTestTags {
    /** The square that holds the code, the loading indicator or the empty and error state. */
    const val AREA = "qr_pass_area"
    const val IMAGE = "qr_pass_image"
    const val LOADING = "qr_pass_loading"
    const val STATE = "qr_pass_state"
    const val REFRESH = "qr_pass_refresh"
}

/**
 * The QR pass: a square area of at most 300 dp that shows the code, the loading indicator or the empty and error
 * state, the instruction under it and a refresh button that never moves between states. Stateless; [QrPassRoute]
 * feeds it. Failed refreshes of a still valid pass show in [snackbarHostState].
 */
@Composable
fun QrPassScreen(
    state: QrCodeUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(Res.string.qr_pass_title),
                navigation = {
                    AppTopBarAction(
                        painterResource(KitRes.drawable.ic_arrow_back),
                        stringResource(CoreRes.string.common_back),
                        onClick = onBack,
                    )
                },
            )
            QrPassBody(state, onRefresh, Modifier.weight(1f))
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun QrPassBody(state: QrCodeUiState, onRefresh: () -> Unit, modifier: Modifier) {
    val content = state as? QrCodeUiState.Content
    val matrix = rememberQrMatrix(content?.code?.hex)
    val showsCode = content != null && matrix != null
    // fillMaxSize before verticalScroll keeps the viewport as the minimum height, so the column centres in it.
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.passArea().testTag(QrPassTestTags.AREA), contentAlignment = Alignment.Center) {
            when {
                content != null && matrix != null -> {
                    val description = stringResource(Res.string.qr_pass_image_description)
                    QrCodeImage(
                        matrix,
                        qrColors(content.useDynamicColors),
                        Modifier
                            .fillMaxSize()
                            .testTag(QrPassTestTags.IMAGE)
                            .semantics {
                                contentDescription = description
                                role = Role.Image
                            },
                    )
                }
                content != null -> PassState(
                    stringResource(CoreRes.string.common_load_error_title),
                    stringResource(Res.string.qr_pass_empty_description),
                )
                state == QrCodeUiState.Loading -> ContentStateLoading(Modifier.testTag(QrPassTestTags.LOADING))
                state == QrCodeUiState.Empty -> PassState(
                    stringResource(Res.string.qr_pass_empty_title),
                    stringResource(Res.string.qr_pass_empty_description),
                )
                state is QrCodeUiState.Error -> PassState(
                    stringResource(CoreRes.string.common_load_error_title),
                    stringResource(state.error.textResource()),
                )
            }
        }
        // Invisible, not gone, outside content: the button below keeps its place in every state.
        Text(
            stringResource(Res.string.qr_pass_description),
            Modifier
                .fillMaxWidth()
                .padding(top = ItmoTheme.spacing.section)
                .alpha(if (showsCode) 1f else 0f)
                .then(if (showsCode) Modifier else Modifier.clearAndSetSemantics {}),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        ProgressButton(
            label = stringResource(Res.string.qr_pass_refresh),
            onClick = onRefresh,
            modifier = Modifier.padding(top = ItmoTheme.spacing.group).testTag(QrPassTestTags.REFRESH),
            style = ProgressButtonStyle.Tonal,
            inProgress = content?.refreshing == true,
            enabled = state != QrCodeUiState.Loading,
            icon = painterResource(KitRes.drawable.ic_refresh),
        )
    }
}

/**
 * The empty or error state, centred on the area. Its height is unbounded, so at a large font in a narrow window the
 * text runs past the square into the space the hidden instruction keeps instead of being cut off; the area itself
 * stays square.
 */
@Composable
private fun PassState(title: String, description: String) {
    ContentState(
        title = title,
        modifier = Modifier.wrapContentHeight(unbounded = true).testTag(QrPassTestTags.STATE),
        icon = painterResource(KitRes.drawable.ic_qr_code),
        description = description,
    )
}

/** The module matrix of [hex], or null when there is none or it does not fit a version 1 code. */
@Composable
private fun rememberQrMatrix(hex: String?): List<List<Boolean>>? = remember(hex) {
    hex?.let { value ->
        runCatching { QrCodeGenerator().let { it.toBooleans(it.generate(value)) } }.getOrNull()
    }
}

/**
 * `fragment_qr_code.xml`'s area: 80 % of the width, at most [AreaMaxSize], square. A layout of its own, since
 * `fillMaxWidth(0.8f)` and `widthIn(max)` in either order give a different size on wide windows.
 */
private fun Modifier.passArea(): Modifier = layout { measurable, constraints ->
    val side = min((constraints.maxWidth * AREA_WIDTH_FRACTION).roundToInt(), AreaMaxSize.roundToPx())
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

private const val AREA_WIDTH_FRACTION = 0.8f
private val AreaMaxSize = 300.dp
