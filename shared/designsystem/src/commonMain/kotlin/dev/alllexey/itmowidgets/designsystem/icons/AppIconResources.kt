package dev.alllexey.itmowidgets.designsystem.icons

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_assignment
import dev.alllexey.itmowidgets.shared.designsystem.ic_book_2
import dev.alllexey.itmowidgets.shared.designsystem.ic_book_2_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_brand_telegram
import dev.alllexey.itmowidgets.shared.designsystem.ic_brand_vk
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_month
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_month_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_chat
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.designsystem.ic_download
import dev.alllexey.itmowidgets.shared.designsystem.ic_edit_note
import dev.alllexey.itmowidgets.shared.designsystem.ic_folder
import dev.alllexey.itmowidgets.shared.designsystem.ic_format_list_numbered
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import dev.alllexey.itmowidgets.shared.designsystem.ic_open_in_new
import dev.alllexey.itmowidgets.shared.designsystem.ic_person
import dev.alllexey.itmowidgets.shared.designsystem.ic_person_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_refresh
import dev.alllexey.itmowidgets.shared.designsystem.ic_school
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint
import dev.alllexey.itmowidgets.shared.designsystem.ic_sprint_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_table
import dev.alllexey.itmowidgets.shared.designsystem.ic_today
import dev.alllexey.itmowidgets.shared.designsystem.ic_today_filled
import dev.alllexey.itmowidgets.shared.designsystem.ic_videocam
import org.jetbrains.compose.resources.DrawableResource

/** The `ic_<id>` drawable of an [AppIcon] for Compose; Views map it in `core/ui/AppIconRes.kt` of `:app`. */
val AppIcon.drawable: DrawableResource
    get() = when (this) {
        AppIcon.ASSIGNMENT -> Res.drawable.ic_assignment
        AppIcon.BOOK_2 -> Res.drawable.ic_book_2
        AppIcon.BOOK_2_FILLED -> Res.drawable.ic_book_2_filled
        AppIcon.BRAND_TELEGRAM -> Res.drawable.ic_brand_telegram
        AppIcon.BRAND_VK -> Res.drawable.ic_brand_vk
        AppIcon.CALENDAR_MONTH -> Res.drawable.ic_calendar_month
        AppIcon.CALENDAR_MONTH_FILLED -> Res.drawable.ic_calendar_month_filled
        AppIcon.CHAT -> Res.drawable.ic_chat
        AppIcon.CHEVRON_RIGHT -> Res.drawable.ic_chevron_right
        AppIcon.DOWNLOAD -> Res.drawable.ic_download
        AppIcon.EDIT_NOTE -> Res.drawable.ic_edit_note
        AppIcon.FOLDER -> Res.drawable.ic_folder
        AppIcon.FORMAT_LIST_NUMBERED -> Res.drawable.ic_format_list_numbered
        AppIcon.LINK -> Res.drawable.ic_link
        AppIcon.OPEN_IN_NEW -> Res.drawable.ic_open_in_new
        AppIcon.PERSON -> Res.drawable.ic_person
        AppIcon.PERSON_FILLED -> Res.drawable.ic_person_filled
        AppIcon.REFRESH -> Res.drawable.ic_refresh
        AppIcon.SCHOOL -> Res.drawable.ic_school
        AppIcon.SPRINT -> Res.drawable.ic_sprint
        AppIcon.SPRINT_FILLED -> Res.drawable.ic_sprint_filled
        AppIcon.TABLE -> Res.drawable.ic_table
        AppIcon.TODAY -> Res.drawable.ic_today
        AppIcon.TODAY_FILLED -> Res.drawable.ic_today_filled
        AppIcon.VIDEOCAM -> Res.drawable.ic_videocam
    }
