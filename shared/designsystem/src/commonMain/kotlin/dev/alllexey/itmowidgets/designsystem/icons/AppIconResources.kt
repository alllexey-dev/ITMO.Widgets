package dev.alllexey.itmowidgets.designsystem.icons

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_download
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.designsystem.ic_refresh
import org.jetbrains.compose.resources.DrawableResource

/** The `ic_<id>` drawable of an [AppIcon] for Compose; Views map it in `core/ui/AppIconRes.kt` of `:app`. */
val AppIcon.drawable: DrawableResource
    get() = when (this) {
        AppIcon.CHEVRON_RIGHT -> Res.drawable.ic_chevron_right
        AppIcon.DOWNLOAD -> Res.drawable.ic_download
        AppIcon.OPEN_IN_NEW -> Res.drawable.ic_open_in_new
        AppIcon.REFRESH -> Res.drawable.ic_refresh
    }
