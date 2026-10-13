package dev.alllexey.itmowidgets.feature.qr.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoLoadingIndicator
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassStatus
import dev.alllexey.itmowidgets.feature.qr.presentation.QrPassUiState
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeImage
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.qrColors
import dev.alllexey.itmowidgets.shared.compose.strings.common_close
import dev.alllexey.itmowidgets.shared.compose.strings.common_load_error_title
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_history
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code
import dev.alllexey.itmowidgets.shared.designsystem.ic_refresh
import dev.alllexey.itmowidgets.shared.feature.qr.Res
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_description
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_empty_description
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_empty_title
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_image_description
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_refresh
import dev.alllexey.itmowidgets.shared.feature.qr.qr_pass_sheet_title
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** Test tags of [QrPassSheetContent], read by host tests and the instrumented flows. */
object QrPassTestTags {
    /** The square that holds the code, the loading indicator or the empty and error state. */
    const val AREA = "qr_pass_area"
    const val IMAGE = "qr_pass_image"
    const val LOADING = "qr_pass_loading"
    const val STATE = "qr_pass_state"
    const val HOLDER = "qr_pass_holder"
    const val LINE = "qr_pass_line"
    const val REFRESH = "qr_pass_refresh"
}

/**
 * The pass sheet: the title with a close button, who the pass belongs to, a square area of at most [AreaMaxSize] with
 * the code (dimmed under the loading indicator while a new one loads), the empty or error state, the instruction
 * under the code and one line: when the code was loaded, or that it is the last one while there is no network, with
 * `Обновить` at its end. No validity or countdown. Stateless; [QrPassRoute] feeds it.
 *
 * The code is the flow's hero: one that arrives while the sheet is open grows and fades in on
 * [ItmoTheme.heroMotionScheme]; a code already there on the first frame, and every code under reduced motion, shows at
 * once.
 */
@Composable
fun QrPassSheetContent(
    state: QrPassUiState,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier) {
        SheetScaffold(
            title = stringResource(Res.string.qr_pass_sheet_title),
            close = SheetClose(stringResource(CoreRes.string.common_close), onClose),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .padding(bottom = ItmoTheme.spacing.compact),
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            ) {
                Holder(state)
                PassArea(state, onRefresh)
                StatusLine(state.status, onRefresh)
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Holder(state: QrPassUiState) {
    val holder = state.holder ?: return
    Column(Modifier.fillMaxWidth().testTag(QrPassTestTags.HOLDER)) {
        holder.name?.let { Text(it, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge) }
        holder.details?.let {
            Text(it.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PassArea(state: QrPassUiState, onRefresh: () -> Unit) {
    val code = state.code
    val matrix = rememberQrMatrix(code?.hex)
    val firstFrame = remember { FirstFrame() }
    SideEffect { firstFrame.passed = true }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.passArea().testTag(QrPassTestTags.AREA), contentAlignment = Alignment.Center) {
            val status = state.status
            when {
                code != null && matrix != null -> key(code.hex) {
                    val description = stringResource(Res.string.qr_pass_image_description)
                    val reveal = rememberHeroReveal(animate = firstFrame.passed)
                    QrCodeImage(
                        matrix,
                        qrColors(code.useDynamicColors),
                        Modifier
                            .fillMaxSize()
                            .alpha(if (status == QrPassStatus.Refreshing) REFRESHING_ALPHA else 1f)
                            .heroReveal(reveal)
                            .testTag(QrPassTestTags.IMAGE)
                            .semantics {
                                contentDescription = description
                                role = Role.Image
                            },
                    )
                    if (status == QrPassStatus.Refreshing) ItmoLoadingIndicator()
                }
                code != null -> PassState(
                    stringResource(CoreRes.string.common_load_error_title),
                    stringResource(Res.string.qr_pass_empty_description),
                )
                status is QrPassStatus.Failed -> PassState(
                    stringResource(CoreRes.string.common_load_error_title),
                    status.error.toUiText().asString(),
                    ContentStateAction(stringResource(CoreRes.string.common_retry), onRefresh),
                )
                status == QrPassStatus.Empty -> PassState(
                    stringResource(Res.string.qr_pass_empty_title),
                    stringResource(Res.string.qr_pass_empty_description),
                    ContentStateAction(stringResource(Res.string.qr_pass_refresh), onRefresh),
                )
                else -> ContentStateLoading(Modifier.testTag(QrPassTestTags.LOADING), ContentStateSize.Compact)
            }
        }
        if (code != null && matrix != null) {
            Text(
                stringResource(Res.string.qr_pass_description),
                Modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.compact),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** When the code was loaded, with `Обновить` at the end; only under a code. */
@Composable
private fun StatusLine(status: QrPassStatus, onRefresh: () -> Unit) {
    val line = status.line ?: return
    val muted = ItmoTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        when (status) {
            QrPassStatus.Refreshing -> ItmoActivityIndicator()
            is QrPassStatus.Offline -> Icon(painterResource(KitRes.drawable.ic_cloud_off), null, tint = muted)
            else -> Icon(painterResource(KitRes.drawable.ic_history), null, tint = muted)
        }
        Text(
            line.asString(),
            Modifier.weight(1f).testTag(QrPassTestTags.LINE),
            color = muted,
            style = ItmoTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = onRefresh,
            modifier = Modifier.testTag(QrPassTestTags.REFRESH),
            enabled = status != QrPassStatus.Refreshing,
        ) {
            Icon(painterResource(KitRes.drawable.ic_refresh), null, Modifier.size(ButtonIconSize))
            Text(
                stringResource(
                    if (status is QrPassStatus.Offline) CoreRes.string.common_retry else Res.string.qr_pass_refresh,
                ),
                Modifier.padding(start = ItmoTheme.spacing.compact),
            )
        }
    }
}

/**
 * The empty or error state, centred on the area. Its height is unbounded, so at a large font in a narrow window the
 * text runs past the square instead of being cut off; the area itself stays square.
 */
@Composable
private fun PassState(title: String, description: String, action: ContentStateAction? = null) {
    ContentState(
        title = title,
        modifier = Modifier.wrapContentHeight(unbounded = true).testTag(QrPassTestTags.STATE),
        size = ContentStateSize.Compact,
        icon = painterResource(KitRes.drawable.ic_qr_code),
        description = description,
        action = action,
    )
}

/** False until the sheet's first composition is applied; a code composed before that needs no reveal. */
private class FirstFrame {
    var passed = false
}

/** Scale and alpha of a code's reveal, at their end values when nothing animates. */
private class HeroReveal(
    val scale: Animatable<Float, AnimationVector1D>,
    val alpha: Animatable<Float, AnimationVector1D>,
)

/**
 * Starts the reveal of a code that has just entered the composition: scale on the hero's spatial spring (it may
 * overshoot), alpha on its effects spring. Without [animate], or under reduced motion, both start at their end.
 */
@Composable
private fun rememberHeroReveal(animate: Boolean): HeroReveal {
    val run = animate && !rememberReducedMotion()
    val scheme = ItmoTheme.heroMotionScheme
    val reveal = remember {
        HeroReveal(Animatable(if (run) REVEAL_START_SCALE else 1f), Animatable(if (run) 0f else 1f))
    }
    LaunchedEffect(reveal) {
        if (!run) return@LaunchedEffect
        launch { reveal.scale.animateTo(1f, scheme.defaultSpatialSpec()) }
        launch { reveal.alpha.animateTo(1f, scheme.defaultEffectsSpec()) }
    }
    return reveal
}

/** Applies [reveal] in a layer read in the draw phase; the code's layout size and semantics stay as they are. */
private fun Modifier.heroReveal(reveal: HeroReveal): Modifier = graphicsLayer {
    scaleX = reveal.scale.value
    scaleY = reveal.scale.value
    alpha = reveal.alpha.value.coerceIn(0f, 1f)
}

/** The module matrix of [hex], or null when there is none or it does not fit a version 1 code. */
@Composable
private fun rememberQrMatrix(hex: String?): List<List<Boolean>>? = remember(hex) {
    hex?.let { value ->
        runCatching { QrCodeGenerator().let { it.toBooleans(it.generate(value)) } }.getOrNull()
    }
}

/**
 * A square of [AREA_WIDTH_FRACTION] of the width, at most [AreaMaxSize]. A layout of its own, since
 * `fillMaxWidth(0.8f)` and `widthIn(max)` in either order give a different size on wide windows.
 */
private fun Modifier.passArea(): Modifier = layout { measurable, constraints ->
    val side = min((constraints.maxWidth * AREA_WIDTH_FRACTION).roundToInt(), AreaMaxSize.roundToPx())
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

private const val AREA_WIDTH_FRACTION = 0.8f
private val AreaMaxSize = 240.dp
private val ButtonIconSize = 18.dp

/** The old code under the loading indicator while a new one loads. */
private const val REFRESHING_ALPHA = 0.25f

/** Where a revealed code starts growing from, as a fraction of its size. */
private const val REVEAL_START_SCALE = 0.85f
