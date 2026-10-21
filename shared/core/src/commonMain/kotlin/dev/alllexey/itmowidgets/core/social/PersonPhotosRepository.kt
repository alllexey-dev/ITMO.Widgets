package dev.alllexey.itmowidgets.core.social

/**
 * My ITMO directory photos by a known ISU, independent of the Backend opt-in. ViewModels put the URL into their
 * screen state; platforms use their existing avatar component. No identity is inferred from a name.
 */
interface PersonPhotosRepository {
    fun cachedPhotoUrl(isu: Int): String?

    /** Missing photos and directory failures return null; cancellation propagates. Concurrent readers share work. */
    suspend fun photoUrl(isu: Int): String?
}
