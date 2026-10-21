package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.social.PersonPhotosRepository

/** Directory photo answers and a controllable suspended lookup, without a platform image loader or network. */
class FakePersonPhotosRepository : PersonPhotosRepository {
    var photos: Map<Int, String?> = emptyMap()
    var cached: Map<Int, String?> = emptyMap()
    var lookup: suspend (Int) -> String? = { photos[it] }
    val calls = mutableListOf<Int>()

    override fun cachedPhotoUrl(isu: Int): String? = cached[isu]

    override suspend fun photoUrl(isu: Int): String? {
        calls += isu
        return lookup(isu)
    }
}
