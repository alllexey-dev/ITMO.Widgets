package dev.alllexey.itmowidgets.feature.settings.presentation

/**
 * Every settings row. [key] is the row's stable string: the renderer keys its views by it and screen tests address
 * rows by it, so it never changes when an entry is renamed.
 */
enum class SettingRowId(val key: String) {
    SCHEDULE_SPORT_AUTO_SIGN("schedule_sport_auto_sign"),
    CALENDAR_SYNC("calendar_sync"),
    ICS_EXPORT("ics_export"),
    COMPACT_WIDGET_NEXT_LESSON_EARLY("compact_widget_next_lesson_early"),
    /** The day widget's teacher, shown while on: the inverse of the stored `full_widget_hide_teacher`. */
    FULL_WIDGET_SHOW_TEACHER("full_widget_show_teacher"),
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
    HOME_PASS_VISIBLE("home_pass_visible"),
    BARS("bars"),
    PAIR_DEVICE("pair_device"),
    CONNECTED_DEVICES("connected_devices"),
    SPORT_TEACHER_FILTER("sport_teacher_filter"),
    SPORT_TIME_FILTER("sport_time_filter"),
    REFRESH_WIDGETS("refresh_widgets"),
    RESTART_ONBOARDING("restart_onboarding"),
    VERSION("app_version"),
    DIAGNOSTICS("diagnostics"),
    PRIVACY_POLICY("privacy_policy"),
    WIDGETS_FOLLOW_THEME("widgets_follow_theme"),
    LICENSE_PLANS("license_plans"),
    // No "map" in the key: the settings forbid map-provider rows by that word.
    LICENSE_MAPLIBRE("license_renderer"),
    LICENSE_NOTO("license_noto"),

    // Navigation rows: `page_` and the lowercase name of the page they open.
    PAGE_SERVICES("page_services"),
    PAGE_NOTIFICATIONS("page_notifications"),
    PAGE_WIDGETS("page_widgets"),
    PAGE_SECTIONS("page_sections"),
    PAGE_DEVICES("page_devices"),
    PAGE_ABOUT("page_about"),
    PAGE_LICENSES("page_licenses"),
    PAGE_APPEARANCE("page_appearance");

    companion object {
        /** The row that opens [page]; the root page is never a row. */
        fun navigation(page: SettingsPage): SettingRowId = when (page) {
            SettingsPage.ROOT -> throw IllegalArgumentException("The root settings page has no navigation row")
            SettingsPage.SERVICES -> PAGE_SERVICES
            SettingsPage.NOTIFICATIONS -> PAGE_NOTIFICATIONS
            SettingsPage.WIDGETS -> PAGE_WIDGETS
            SettingsPage.SECTIONS -> PAGE_SECTIONS
            SettingsPage.DEVICES -> PAGE_DEVICES
            SettingsPage.ABOUT -> PAGE_ABOUT
            SettingsPage.LICENSES -> PAGE_LICENSES
            SettingsPage.APPEARANCE -> PAGE_APPEARANCE
        }
    }
}
