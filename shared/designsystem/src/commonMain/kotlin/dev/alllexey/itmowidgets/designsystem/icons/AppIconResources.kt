package dev.alllexey.itmowidgets.designsystem.icons

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_assignment
import dev.alllexey.itmowidgets.shared.designsystem.ic_brand_telegram
import dev.alllexey.itmowidgets.shared.designsystem.ic_brand_vk
import dev.alllexey.itmowidgets.shared.designsystem.ic_chat
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_download
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_note
import dev.alllexey.itmowidgets.shared.designsystem.ic_folder
import dev.alllexey.itmowidgets.shared.designsystem.ic_format_list_numbered
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.designsystem.ic_refresh
import dev.alllexey.itmowidgets.shared.designsystem.ic_school
import dev.alllexey.itmowidgets.shared.designsystem.ic_table
import dev.alllexey.itmowidgets.shared.designsystem.ic_videocam
import org.jetbrains.compose.resources.DrawableResource

/** The `ic_<id>` drawable of an [AppIcon] for Compose; Views map it in `core/ui/AppIconRes.kt` of `:app`. */
val AppIcon.drawable: DrawableResource
    get() = when (this) {
        AppIcon.ASSIGNMENT -> Res.drawable.ic_assignment
        AppIcon.BRAND_TELEGRAM -> Res.drawable.ic_brand_telegram
        AppIcon.BRAND_VK -> Res.drawable.ic_brand_vk
        AppIcon.CHAT -> Res.drawable.ic_chat
        AppIcon.CHEVRON_RIGHT -> Res.drawable.ic_chevron_right
        AppIcon.DOWNLOAD -> Res.drawable.ic_download
        AppIcon.EDIT_NOTE -> Res.drawable.ic_edit_note
        AppIcon.FOLDER -> Res.drawable.ic_folder
        AppIcon.FORMAT_LIST_NUMBERED -> Res.drawable.ic_format_list_numbered
        AppIcon.LINK -> Res.drawable.ic_link
        AppIcon.OPEN_IN_NEW -> Res.drawable.ic_open_in_new
        AppIcon.REFRESH -> Res.drawable.ic_refresh
        AppIcon.SCHOOL -> Res.drawable.ic_school
        AppIcon.TABLE -> Res.drawable.ic_table
        AppIcon.VIDEOCAM -> Res.drawable.ic_videocam
    }
