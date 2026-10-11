package dev.alllexey.itmowidgets.designsystem.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.settings.HexColor
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import kotlin.math.max
import kotlin.math.min

/** The texts of a [ColorPicker], from the caller's strings. */
@Immutable
data class ColorPickerLabels(
    val hue: String,
    val saturation: String,
    val brightness: String,
    val hex: String,
    val hexError: String,
    val preview: String,
)

/** Test tags of a [ColorPicker]'s parts. */
object ColorPickerTags {
    const val HUE = "color_picker_hue"
    const val SATURATION = "color_picker_saturation"
    const val BRIGHTNESS = "color_picker_brightness"
    const val HEX = "color_picker_hex"
}

/**
 * Picks an opaque colour: three sliders (hue, saturation, brightness, each track drawn in the colours it gives) and a
 * `#RRGGBB` field with a swatch of the colour in it. The sliders and the field follow each other at once; the colour
 * reaches [onColorChange] when a slider is let go or the field holds a valid value, so a caller that rebuilds the
 * app's scheme does it once per pick and not on every step of a drag. The field takes six hex digits after an
 * optional `#`; an unfinished value shows [ColorPickerLabels.hexError] once the field loses focus (Done on the
 * keyboard drops it). A new [argb] from the caller replaces the picker's own value.
 */
@Composable
fun ColorPicker(argb: Int, onColorChange: (Int) -> Unit, labels: ColorPickerLabels, modifier: Modifier = Modifier) {
    var hsv by remember { mutableStateOf(Hsv.of(argb)) }
    var text by remember { mutableStateOf(HexColor.format(argb)) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(argb) {
        if (hsv.argb != argb) {
            hsv = Hsv.of(argb)
            text = HexColor.format(argb)
        }
    }
    val pick = { next: Hsv ->
        hsv = next
        text = HexColor.format(next.argb)
    }
    val commit = { onColorChange(hsv.argb) }
    Column(modifier.fillMaxWidth().padding(top = ItmoTheme.spacing.content)) {
        val showError = !focused && HexColor.parse(text) == null
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                // Hex digits after an optional leading '#', six at most, so a seventh key press changes nothing.
                val digits = typed.filter { it.isHexDigit() }.take(HEX_DIGITS).uppercase()
                text = if (typed.startsWith("#")) "#$digits" else digits
                HexColor.parse(text)?.let { parsed ->
                    hsv = Hsv.of(parsed)
                    onColorChange(parsed)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ColorPickerTags.HEX)
                .onFocusChanged { focused = it.isFocused },
            label = { Text(labels.hex) },
            leadingIcon = { PreviewSwatch(Color(hsv.argb), labels.preview) },
            isError = showError,
            // Always laid out, so the error line does not move the sliders.
            supportingText = { if (showError) Text(labels.hexError) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
        ChannelSlider(
            label = labels.hue,
            value = hsv.hue,
            range = 0f..360f,
            colors = HueStops.map { Color.hsv(it, 1f, 1f) },
            onChange = { pick(hsv.copy(hue = it)) },
            onDone = commit,
            tag = ColorPickerTags.HUE,
        )
        ChannelSlider(
            label = labels.saturation,
            value = hsv.saturation,
            range = 0f..1f,
            colors = listOf(Color.hsv(hsv.hue, 0f, hsv.value), Color.hsv(hsv.hue, 1f, hsv.value)),
            onChange = { pick(hsv.copy(saturation = it)) },
            onDone = commit,
            tag = ColorPickerTags.SATURATION,
        )
        ChannelSlider(
            label = labels.brightness,
            value = hsv.value,
            range = 0f..1f,
            colors = listOf(Color.Black, Color.hsv(hsv.hue, hsv.saturation, 1f)),
            onChange = { pick(hsv.copy(value = it)) },
            onDone = commit,
            tag = ColorPickerTags.BRIGHTNESS,
        )
    }
}

@Composable
private fun PreviewSwatch(color: Color, description: String) {
    Box(
        Modifier
            .size(SwatchSize)
            .background(color, CircleShape)
            .border(1.dp, ItmoTheme.colorScheme.outlineVariant, CircleShape)
            .semantics { contentDescription = description },
    )
}

/** A labelled slider of one channel over a track in the colours it gives; at least a touch target high. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: List<Color>,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    tag: String,
) {
    Text(
        label,
        Modifier.padding(top = ItmoTheme.spacing.compact),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.labelLarge,
    )
    Slider(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .testTag(tag)
            .semantics { contentDescription = label },
        onValueChangeFinished = onDone,
        valueRange = range,
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TrackHeight)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(colors))
                    .border(1.dp, ItmoTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
    )
}

/** Hue in degrees, saturation and value in 0..1: the picker's own model, so a grey keeps the hue it was dragged from. */
private data class Hsv(val hue: Float, val saturation: Float, val value: Float) {
    val argb: Int get() = Color.hsv(hue, saturation, value).toArgb()

    companion object {
        fun of(argb: Int): Hsv {
            val r = (argb shr 16 and 0xFF) / 255f
            val g = (argb shr 8 and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val high = max(r, max(g, b))
            val delta = high - min(r, min(g, b))
            val hue = when {
                delta == 0f -> 0f
                high == r -> 60f * (((g - b) / delta) % 6f)
                high == g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }
            return Hsv((hue + 360f) % 360f, if (high == 0f) 0f else delta / high, high)
        }
    }
}

private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private const val HEX_DIGITS = 6
private val HueStops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
private val SwatchSize = 24.dp
private val TrackHeight = 12.dp
