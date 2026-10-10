package dev.alllexey.itmowidgets.feature.settings.presentation

/**
 * Every settings row. [key] is the row's stable string: the renderer keys its views by it and screen tests address
 * rows by it, so it never changes when an entry is renamed.
 */
enum class SettingRowId(val key: String) {
    CUSTOM_SERVICES("custom_services"),
    NOTIFICATIONS("notifications"),
    SCHEDULE_SHARING("schedule_sharing"),
    SPORT_SHARING("sport_sharing"),
    FRIENDS_SHARING("friends_sharing"),
    SCHEDULE_SPORT_AUTO_SIGN("schedule_sport_auto_sign"),
    SCHEDULE_CHANGES("schedule_changes"),
    HOME_CARD_SCHEDULE("home_card_schedule"),
    HOME_CARD_SCHEDULE_CHANGES("home_card_schedule_changes"),
    HOME_CARD_MARKS("home_card_marks"),
    HOME_CARD_SPORT("home_card_sport"),
    HOME_CARD_FRIENDS("home_card_friends"),
    MYITMO_MARKS("myitmo_marks"),
    BARS_MARKS("bars_marks"),
    SHEET_MARKS("sheet_marks"),
    BACKGROUND_WORK("background_work"),
    CALENDAR_SYNC("calendar_sync"),
    ICS_EXPORT("ics_export"),
    RETRY_PRIVACY("retry_privacy"),
    COMPACT_WIDGET_NEXT_LESSON_EARLY("compact_widget_next_lesson_early"),
    COMPACT_WIDGET_HIDE_TEACHER("compact_widget_hide_teacher"),
    FULL_WIDGET_HIDE_TEACHER("full_widget_hide_teacher"),
    FULL_WIDGET_HIDE_PAST("full_widget_hide_past"),
    FULL_WIDGET_SHOW_TOMORROW("full_widget_show_tomorrow"),
    COMPACT_WIDGET_TEXT_SIZE("compact_widget_text_size"),
    FULL_WIDGET_TEXT_SIZE("full_widget_text_size"),
    QR_DYNAMIC_COLORS("qr_dynamic_colors"),
    QR_SPOILER("qr_spoiler"),
    QR_ANIMATION("qr_animation"),
    QR_CUSTOM_IMAGE("qr_custom_image"),
    QR_RESET_IMAGE("qr_reset_image"),
    QR_TILE("qr_tile"),
    SPORT_TEACHER_FILTER("sport_teacher_filter"),
    SPORT_TIME_FILTER("sport_time_filter"),
    REFRESH_WIDGETS("refresh_widgets"),
    RESTART_ONBOARDING("restart_onboarding"),
    VERSION("app_version"),
    DIAGNOSTICS("diagnostics"),
    DELETE_ACCOUNT("delete_account"),
    PRIVACY_POLICY("privacy_policy"),
    ACCENT_COLOR("accent_color"),
    ACCENT_CUSTOM("accent_custom"),
    THEME_PALETTE("theme_palette"),
    THEME_CONTRAST("theme_contrast"),
    DARK_BLACK("dark_black"),

    // Navigation rows: `page_` and the lowercase name of the page they open.
    PAGE_SERVICES("page_services"),
    PAGE_PRIVACY("page_privacy"),
    PAGE_COMPACT_SCHEDULE_WIDGET("page_compact_schedule_widget"),
    PAGE_FULL_SCHEDULE_WIDGET("page_full_schedule_widget"),
    PAGE_QR_WIDGET("page_qr_widget"),
    PAGE_HOME("page_home"),
    PAGE_SCHEDULE("page_schedule"),
    PAGE_RECORDBOOK("page_recordbook"),
    PAGE_SPORT("page_sport"),
    PAGE_MAINTENANCE("page_maintenance"),
    PAGE_APPEARANCE("page_appearance");

    companion object {
        /** The row that opens [page]; the root page is never a row. */
        fun navigation(page: SettingsPage): SettingRowId = when (page) {
            SettingsPage.ROOT -> throw IllegalArgumentException("The root settings page has no navigation row")
            SettingsPage.SERVICES -> PAGE_SERVICES
            SettingsPage.PRIVACY -> PAGE_PRIVACY
            SettingsPage.COMPACT_SCHEDULE_WIDGET -> PAGE_COMPACT_SCHEDULE_WIDGET
            SettingsPage.FULL_SCHEDULE_WIDGET -> PAGE_FULL_SCHEDULE_WIDGET
            SettingsPage.QR_WIDGET -> PAGE_QR_WIDGET
            SettingsPage.HOME -> PAGE_HOME
            SettingsPage.SCHEDULE -> PAGE_SCHEDULE
            SettingsPage.RECORDBOOK -> PAGE_RECORDBOOK
            SettingsPage.SPORT -> PAGE_SPORT
            SettingsPage.MAINTENANCE -> PAGE_MAINTENANCE
            SettingsPage.APPEARANCE -> PAGE_APPEARANCE
        }
    }
}
