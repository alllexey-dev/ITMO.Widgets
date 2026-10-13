package dev.alllexey.itmowidgets.core.text

/**
 * An icon that presentation state names without a platform resource. [id] is the row of `docs/design/icons.tsv`
 * and the drawable `ic_<id>`; Compose maps it in `designsystem/icons/AppIconResources.kt`, Swift through the
 * registry's SF Symbol. Add a constant when presentation first needs the icon.
 */
enum class AppIcon(val id: String) {
    ASSIGNMENT("assignment"),
    BOOK_2("book_2"),
    BOOK_2_FILLED("book_2_filled"),
    BRAND_TELEGRAM("brand_telegram"),
    BRAND_VK("brand_vk"),
    CALENDAR_MONTH("calendar_month"),
    CALENDAR_MONTH_FILLED("calendar_month_filled"),
    CHAT("chat"),
    CHEVRON_RIGHT("chevron_right"),
    DOWNLOAD("download"),
    EDIT_NOTE("edit_note"),
    FOLDER("folder"),
    FORMAT_LIST_NUMBERED("format_list_numbered"),
    LINK("link"),
    OPEN_IN_NEW("open_in_new"),
    PERSON("person"),
    PERSON_FILLED("person_filled"),
    REFRESH("refresh"),
    SCHOOL("school"),
    SPRINT("sprint"),
    SPRINT_FILLED("sprint_filled"),
    TABLE("table"),
    TODAY("today"),
    TODAY_FILLED("today_filled"),
    VIDEOCAM("videocam")
}
