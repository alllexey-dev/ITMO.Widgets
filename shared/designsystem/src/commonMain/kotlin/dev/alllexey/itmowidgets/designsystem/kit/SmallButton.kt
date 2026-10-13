package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

/**
 * An extra-small filled M3E button with a short [text] and an optional [icon], for an action next to a hero's big
 * text (`Войти` on the Сегодня hero). One per area, like any filled button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Painter? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
        contentPadding = ButtonDefaults.ExtraSmallContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(ButtonDefaults.ExtraSmallIconSize))
        }
        Text(text, if (icon != null) Modifier.padding(start = ButtonDefaults.ExtraSmallIconSpacing) else Modifier)
    }
}
