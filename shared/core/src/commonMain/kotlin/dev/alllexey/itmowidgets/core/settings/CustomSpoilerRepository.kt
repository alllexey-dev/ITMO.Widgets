package dev.alllexey.itmowidgets.core.settings

/**
 * The image the QR widget shows in place of the code until it is tapped.
 *
 * A successful save or reset also refreshes the pinned widgets, exactly as a
 * [WidgetAppearanceRepository] write does; a failed one leaves the previous
 * image readable and touches nothing.
 */
interface CustomSpoilerRepository {
    suspend fun hasImage(): Boolean
    suspend fun saveImage(sourceUri: String): Boolean
    suspend fun resetImage(): Boolean
}
