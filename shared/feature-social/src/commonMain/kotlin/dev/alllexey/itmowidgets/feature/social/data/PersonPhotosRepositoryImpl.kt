package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.social.PersonPhotosRepository
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** One session's person cache, shared requests by ISU and at most four directory lookups across both screens. */
class PersonPhotosRepositoryImpl(
    private val people: PersonRepository,
    private val demo: DemoMode,
    private val tokens: SessionTokenStore,
) : PersonPhotosRepository {
    private val requests = mutableMapOf<Int, Request>()
    private val requestsLock = Mutex()
    private val limit = Semaphore(MAX_REQUESTS)

    override fun cachedPhotoUrl(isu: Int): String? = people.cachedPerson(isu)?.photoUrl

    override suspend fun photoUrl(isu: Int): String? {
        if (isu <= 0) return null
        if (!demo.isActive() && !tokens.hasRefreshToken()) return null
        people.cachedPerson(isu)?.let { return it.photoUrl }
        val request = requestsLock.withLock {
            requests.getOrPut(isu) { Request() }.also { it.readers++ }
        }
        try {
            return request.lock.withLock {
                if (!request.done) {
                    request.url = limit.withPermit {
                        // A person without a photo is cached too; a profile may have loaded them while we waited.
                        val cached = people.cachedPerson(isu)
                        if (cached != null) cached.photoUrl else people.person(isu).valueOrNull()?.photoUrl
                    }
                    request.done = true
                }
                request.url
            }
        } finally {
            // An owner cancelling releases the lookup to another reader, without cancelling that reader's screen.
            // Drop completed answers after the last reader so a failed lookup can retry on a subsequent visit.
            withContext(NonCancellable) {
                requestsLock.withLock {
                    request.readers--
                    if (request.readers == 0) requests.remove(isu)
                }
            }
        }
    }

    private class Request {
        val lock = Mutex()
        var readers = 0
        var done = false
        var url: String? = null
    }

    private companion object {
        const val MAX_REQUESTS = 4
    }
}
