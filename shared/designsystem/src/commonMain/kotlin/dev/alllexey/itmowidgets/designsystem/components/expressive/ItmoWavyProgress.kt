package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** The two forms of [ItmoWavyProgress]. */
enum class ItmoWavyProgressShape { Linear, Circular }

/**
 * Determinate progress of a hero moment only (the QR pass reveal, the profile hero, the sport score ring, the lesson
 * in progress); dense rows keep a flat linear bar. [progress] is read in the draw phase, 0..1. With
 * [ItmoTheme.expressive] the track waves; otherwise it is today's flat indicator.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoWavyProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    shape: ItmoWavyProgressShape = ItmoWavyProgressShape.Linear,
) {
    val expressive = ItmoTheme.expressive
    when (shape) {
        ItmoWavyProgressShape.Linear -> if (expressive) {
            LinearWavyProgressIndicator(progress, modifier)
        } else {
            LinearProgressIndicator(progress, modifier)
        }

        ItmoWavyProgressShape.Circular -> if (expressive) {
            CircularWavyProgressIndicator(progress, modifier)
        } else {
            CircularProgressIndicator(progress, modifier)
        }
    }
}
