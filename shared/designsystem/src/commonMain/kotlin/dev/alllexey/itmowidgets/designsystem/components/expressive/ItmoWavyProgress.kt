package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** The two forms of [ItmoWavyProgress]. */
enum class ItmoWavyProgressShape { Linear, Circular }

/**
 * Determinate progress of a hero moment only (the QR pass reveal, the profile hero, the sport score ring, the lesson
 * in progress); dense rows keep a flat linear bar. [progress] is read in the draw phase, 0..1. With
 * [ItmoTheme.expressive] the track waves; otherwise it is today's flat indicator.
 *
 * The wave stands still (`waveSpeed = 0.dp`): Material's default slides it forever, an infinite animation that keeps
 * drawing frames while the bar is on screen, even under an overlay, so the main thread never goes idle (the
 * instrumented suite hung in `Instrumentation.waitForIdleSync` under the home feed's lesson in progress). The bar only
 * moves when [progress] changes.
 *
 * The track is [ItmoTheme.progressTrack] in every form: a groove in the hue of the card it stands on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoWavyProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    shape: ItmoWavyProgressShape = ItmoWavyProgressShape.Linear,
) {
    val expressive = ItmoTheme.expressive
    val track = ItmoTheme.progressTrack
    when (shape) {
        ItmoWavyProgressShape.Linear -> if (expressive) {
            LinearWavyProgressIndicator(progress, modifier, trackColor = track, waveSpeed = 0.dp)
        } else {
            LinearProgressIndicator(progress, modifier, trackColor = track)
        }

        ItmoWavyProgressShape.Circular -> if (expressive) {
            CircularWavyProgressIndicator(progress, modifier, trackColor = track, waveSpeed = 0.dp)
        } else {
            CircularProgressIndicator(progress, modifier, trackColor = track)
        }
    }
}
