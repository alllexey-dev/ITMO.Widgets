package dev.alllexey.itmowidgets.feature.update.domain

/**
 * The iOS update channel: the app's App Store page and nothing else (no GitHub or Play link). The ID is the build
 * setting `APP_STORE_ID`, empty until the app has an App Store record (T13); an empty or malformed ID gives no page,
 * and without a page the app never offers an update.
 */
object AppStoreListing {

    /** `https://apps.apple.com/app/id<id>` for a numeric App Store ID; null otherwise. */
    fun url(appStoreId: String?): String? {
        val id = appStoreId?.trim().orEmpty()
        if (id.isEmpty() || !id.all { it in '0'..'9' }) return null
        return "https://apps.apple.com/app/id$id"
    }
}
