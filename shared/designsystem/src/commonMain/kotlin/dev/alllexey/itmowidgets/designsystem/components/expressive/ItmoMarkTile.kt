package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The app mark's tile on the sign-in screen: with [ItmoTheme.expressive] the twelve-sided cookie of M3 Expressive,
 * otherwise the large rounded square, in `surfaceContainerHigh`, with [content] centred.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoMarkTile(size: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = if (ItmoTheme.expressive) MaterialShapes.Cookie12Sided.toShape() else ItmoTheme.shapes.extraLarge
    Box(
        modifier.size(size).clip(shape).background(ItmoTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
