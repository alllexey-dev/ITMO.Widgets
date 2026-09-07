package dev.alllexey.itmowidgets.feature.settings.domain

interface CustomSpoilerRepository {
    suspend fun hasImage(): Boolean
    suspend fun saveImage(sourceUri: String): Boolean
    suspend fun resetImage(): Boolean
}
