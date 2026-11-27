package dev.alllexey.itmowidgets.core.services

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings

/**
 * One kind of data the ITMO.Widgets server keeps once the user is connected: [text] for a row of its own, [brief] for
 * the one-line list on the first-run step, joined with commas.
 */
data class StoredDatum(val icon: AppIcon, val text: UiText, val brief: UiText)

/**
 * What the server keeps, named once for «Сервер ITMO.Widgets», the consent sheet and the first-run step.
 *
 * **The list must match the Backend schema.** It mirrors the tables `users` (ISU, name, photo), `user_groups`,
 * `lessons`, `user_sport_lessons` with `sport_*_sign_entries`, the private links of `resources` and `devices`. A new
 * table with user data adds a line here with its `services_stored_*` and `services_stored_*_short` strings.
 */
object ServicesStoredData {
    val items: List<StoredDatum> = listOf(
        StoredDatum(
            AppIcon.BADGE,
            UiText.Res(CoreStrings.services_stored_identity),
            UiText.Res(CoreStrings.services_stored_identity_short),
        ),
        StoredDatum(
            AppIcon.GROUP,
            UiText.Res(CoreStrings.services_stored_group),
            UiText.Res(CoreStrings.services_stored_group_short),
        ),
        StoredDatum(
            AppIcon.CALENDAR_MONTH,
            UiText.Res(CoreStrings.services_stored_schedule),
            UiText.Res(CoreStrings.services_stored_schedule_short),
        ),
        StoredDatum(
            AppIcon.SPRINT,
            UiText.Res(CoreStrings.services_stored_sport),
            UiText.Res(CoreStrings.services_stored_sport_short),
        ),
        StoredDatum(
            AppIcon.LINK,
            UiText.Res(CoreStrings.services_stored_links),
            UiText.Res(CoreStrings.services_stored_links_short),
        ),
        StoredDatum(
            AppIcon.NOTIFICATION,
            UiText.Res(CoreStrings.services_stored_device),
            UiText.Res(CoreStrings.services_stored_device_short),
        ),
    )
}
