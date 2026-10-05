package dev.alllexey.itmowidgets.core.ui

import androidx.annotation.DrawableRes
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.text.AppIcon

/** The `ic_<id>` drawable of an [AppIcon] for Views; Compose reads `Res.drawable` (KM-09b). */
@DrawableRes
fun AppIcon.drawableRes(): Int = when (this) {
    AppIcon.ASSIGNMENT -> R.drawable.ic_assignment
    AppIcon.BRAND_TELEGRAM -> R.drawable.ic_brand_telegram
    AppIcon.BRAND_VK -> R.drawable.ic_brand_vk
    AppIcon.CHAT -> R.drawable.ic_chat
    AppIcon.CHEVRON_RIGHT -> R.drawable.ic_chevron_right
    AppIcon.DOWNLOAD -> R.drawable.ic_download
    AppIcon.EDIT_NOTE -> R.drawable.ic_edit_note
    AppIcon.FOLDER -> R.drawable.ic_folder
    AppIcon.FORMAT_LIST_NUMBERED -> R.drawable.ic_format_list_numbered
    AppIcon.LINK -> R.drawable.ic_link
    AppIcon.OPEN_IN_NEW -> R.drawable.ic_open_in_new
    AppIcon.REFRESH -> R.drawable.ic_refresh
    AppIcon.SCHOOL -> R.drawable.ic_school
    AppIcon.TABLE -> R.drawable.ic_table
    AppIcon.VIDEOCAM -> R.drawable.ic_videocam
}
