package dev.alllexey.itmowidgets.core.ui

import androidx.annotation.DrawableRes
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.text.AppIcon

/** The `ic_<id>` drawable of an [AppIcon] for Views; Compose reads `Res.drawable` (KM-09b). */
@DrawableRes
fun AppIcon.drawableRes(): Int = when (this) {
    AppIcon.CHEVRON_RIGHT -> R.drawable.ic_chevron_right
    AppIcon.DOWNLOAD -> R.drawable.ic_download
    AppIcon.OPEN_IN_NEW -> R.drawable.ic_open_in_new
    AppIcon.REFRESH -> R.drawable.ic_refresh
}
