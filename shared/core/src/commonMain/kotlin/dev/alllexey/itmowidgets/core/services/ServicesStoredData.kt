package dev.alllexey.itmowidgets.core.services

import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings

/** One kind of data the ITMO.Widgets server keeps once the user is connected. */
data class StoredDatum(val icon: AppIcon, val text: UiText)

/**
 * What the server keeps, named once for «Сервер ITMO.Widgets», the consent sheet and the first-run step.
 *
 * **The list must match the Backend schema.** It mirrors the tables `users` (ISU, name, photo), `user_groups`,
 * `lessons`, `user_sport_lessons` with `sport_*_sign_entries`, the private links of `resources` and `devices`. A new
 * table with user data adds a line here and its `services_stored_*` string.
 */
object ServicesStoredData {
    val items: List<StoredDatum> = listOf(
        StoredDatum(AppIcon.BADGE, UiText.Res(CoreStrings.services_stored_identity)),
        StoredDatum(AppIcon.GROUP, UiText.Res(CoreStrings.services_stored_group)),
        StoredDatum(AppIcon.CALENDAR_MONTH, UiText.Res(CoreStrings.services_stored_schedule)),
        StoredDatum(AppIcon.SPRINT, UiText.Res(CoreStrings.services_stored_sport)),
        StoredDatum(AppIcon.LINK, UiText.Res(CoreStrings.services_stored_links)),
        StoredDatum(AppIcon.NOTIFICATION, UiText.Res(CoreStrings.services_stored_device)),
    )
}
