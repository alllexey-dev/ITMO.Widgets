package dev.alllexey.itmowidgets.feature.resources.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.resources.ModerationReportRequest
import dev.alllexey.itmowidgets.core.model.resources.PinSubjectLinkRequest
import dev.alllexey.itmowidgets.core.model.resources.ResourceVoteRequest
import dev.alllexey.itmowidgets.core.model.resources.SaveSubjectLinkRequest
import dev.alllexey.itmowidgets.core.model.resources.SubjectLinksResponse
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.WallClock
import java.net.URI
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import dev.alllexey.itmowidgets.core.model.resources.SubjectLink as WireLink

/**
 * With the opt-in every action goes to the server at once and its answer updates the cached snapshot;
 * without it links are device-only PRIVATE links in the same file until a refresh with the opt-in uploads them.
 */
@Singleton
class SubjectLinksRepositoryImpl @Inject constructor(
    private val storage: SubjectLinksFileStore,
    private val api: ItmoWidgetsApi,
    private val services: CustomServicesRepository,
    @param:WallClock private val clock: Clock,
) : SubjectLinksRepository, SessionDataCleaner {
    private val state = MutableStateFlow<StoredLinks?>(null)
    private val loadError = MutableStateFlow<AppError?>(null)
    private val scopeErrors = MutableStateFlow<Map<String, AppError>>(emptyMap())
    private val refreshing = MutableStateFlow<Set<String>>(emptySet())
    private val restrictions = MutableStateFlow<List<UserRestriction>>(emptyList())
    private val stateLock = Mutex()
    private val networkLock = Mutex()
    private val clearLock = Mutex()
    private val epoch = AtomicLong()
    private val activeRequests = ConcurrentHashMap.newKeySet<Job>()
    @Volatile private var clearing = false
    @Volatile private var enabled: Boolean? = null

    override fun observe(scope: ResourceScope): Flow<SubjectLinksState> = combine(
        state, services.observeEnabled().onEach { enabled = it }, loadError, scopeErrors, refreshing,
    ) { data, on, failure, errors, active ->
        val error = errors[scope.key]
        val snapshot = data?.let { snapshot(it, scope, on, failed = error != null) }
        if (snapshot != null) SubjectLinksState.Content(snapshot, scope.key in active)
        else (failure ?: error)?.let(SubjectLinksState::Error) ?: SubjectLinksState.Loading
    }.distinctUntilChanged().onStart { attempt { load(epoch.get()) } }

    override fun peek(scope: ResourceScope): SubjectLinksSnapshot? {
        val data = state.value ?: return null
        val on = enabled ?: return null
        return snapshot(data, scope, on, failed = scope.key in scopeErrors.value)
    }

    override suspend fun refresh(scope: ResourceScope): AppResult<Unit> = attempt {
        val generation = epoch.get()
        load(generation)
        if (!isEnabled()) return@attempt
        refreshing.update { it + scope.key }
        try {
            networkLock.withLock {
                upload(generation)
                fetch(generation, scope)
            }
            scopeErrors.update { it - scope.key }
        } catch (failure: Exception) {
            val error = failure.appError()
            if (failure !is CancellationException && error != AppError.Unauthorized) scopeErrors.update { it + (scope.key to error) }
            throw failure
        } finally {
            refreshing.update { it - scope.key }
        }
    }

    override suspend fun save(
        scope: ResourceScope,
        id: String,
        category: LinkCategory,
        url: String,
        title: String?,
        visibility: LinkVisibility,
        flowId: Long?,
    ): AppResult<SubjectLink> = attempt {
        val generation = epoch.get()
        load(generation)
        val request = saveRequest(scope, id, category, url, title, visibility, flowId)
        if (!isEnabled()) {
            val onServer = current().scopes.values.any { cached -> cached.response.mine.any { it.id.toString() == id } }
            if (visibility != LinkVisibility.PRIVATE || onServer) throw Failure(AppError.CustomServicesDisabled)
            val link = LocalLink(id, request, OffsetDateTime.ofInstant(clock.instant(), clock.zone))
            mutate(generation) { it.copy(local = it.local + (id to link)) }
            return@attempt link.toModel()
        }
        networkLock.withLock {
            val answer = restricted(generation) { request(generation) { api.saveSubjectLink(UUID.fromString(id), request) } }
            mutate(generation) { it.copy(local = it.local - id).withMine(scope, answer) }
            if (current().scopes[scope.key] == null) {
                try { fetch(generation, scope) } catch (_: Failure) { /* The saved link comes with the next refresh. */ }
            }
            answer.toModel()
        }
    }

    override suspend fun delete(scope: ResourceScope, id: String): AppResult<Unit> = attempt {
        val generation = epoch.get()
        load(generation)
        if (id in current().local) {
            mutate(generation) { data -> data.copy(local = data.local - id, localPins = data.localPins.filterValues { it.linkId != id }) }
            return@attempt
        }
        networkLock.withLock {
            call(generation) { api.deleteSubjectLink(UUID.fromString(id)) }
            mutate(generation) { it.withoutMine(scope, id) }
        }
    }

    override suspend fun pin(scope: ResourceScope, id: String?): AppResult<Unit> = attempt {
        val generation = epoch.get()
        load(generation)
        if (!isEnabled()) {
            if (id == null) {
                mutate(generation) { it.copy(localPins = it.localPins - scope.key) }
                return@attempt
            }
            if (current().local[id]?.scope?.key != scope.key) throw Failure(AppError.CustomServicesDisabled)
            mutate(generation) { it.copy(localPins = it.localPins + (scope.key to LocalPin(scope, id))) }
            return@attempt
        }
        networkLock.withLock {
            val answer = request(generation) {
                api.pinSubjectLink(scope.subjectId, PinSubjectLinkRequest(scope.periodKey, id?.let(UUID::fromString)))
            }
            mutate(generation) { it.copy(localPins = it.localPins - scope.key).withResponse(scope, answer) }
        }
    }

    override suspend fun vote(scope: ResourceScope, id: String, value: Int): AppResult<Unit> {
        require(value in -1..1)
        return linkAction(scope) { api.voteSubjectLink(UUID.fromString(id), ResourceVoteRequest(value)) }
    }

    override suspend fun report(scope: ResourceScope, id: String, reason: ResourceReportReason, comment: String?): AppResult<Unit> =
        linkAction(scope) {
            api.reportSubjectLink(UUID.fromString(id), ModerationReportRequest(reason.toWire(), comment?.trim()?.takeIf { it.isNotEmpty() }))
        }

    override fun observeRestrictions(): Flow<List<UserRestriction>> = combine(restrictions, services.observeEnabled(), flow {
        while (true) { emit(clock.instant()); delay(RESTRICTION_TICK_MILLIS) }
    }) { rows, on, now -> if (!on) emptyList() else rows.filter { it.expiresAt?.toInstant()?.isAfter(now) != false } }
        .distinctUntilChanged()

    override suspend fun refreshRestrictions(): AppResult<Unit> = attempt {
        val generation = epoch.get()
        if (!isEnabled()) {
            restrictions.value = emptyList()
            return@attempt
        }
        networkLock.withLock { fetchRestrictions(generation) }
    }

    override suspend fun clearSessionData() {
        clearLock.withLock {
            val calls = stateLock.withLock {
                clearing = true
                epoch.incrementAndGet()
                activeRequests.toList()
            }
            try {
                // SessionRepository replaces tokens only after cleaners finish. Cancel and join so that an old call
                // cannot start with the next account's token, not merely so that its answer is dropped.
                calls.forEach { it.cancel() }
                calls.joinAll()
                stateLock.withLock {
                    withContext(Dispatchers.IO) { storage.clear() }
                    state.value = StoredLinks()
                    loadError.value = null
                    scopeErrors.value = emptyMap()
                    refreshing.value = emptySet()
                    restrictions.value = emptyList()
                }
            } finally {
                clearing = false
            }
        }
    }

    /** Device-only links become PRIVATE server links; one the server refuses stays local and is retried next time. */
    private suspend fun upload(generation: Long) {
        for (link in current().local.values.toList()) {
            val answer = try {
                request(generation) { api.saveSubjectLink(UUID.fromString(link.id), link.request) }
            } catch (failure: Failure) {
                if (failure.error.refusesItem) continue else throw failure
            }
            mutate(generation) { data ->
                // An offline edit made while the upload was in flight is newer than the answer; it is sent next time.
                val next = if (data.local[link.id] == link) data.copy(local = data.local - link.id) else data
                next.withMine(link.scope, answer)
            }
        }
        for (pin in current().localPins.values.toList()) {
            if (pin.linkId in current().local) continue
            val answer = try {
                request(generation) {
                    api.pinSubjectLink(pin.scope.subjectId, PinSubjectLinkRequest(pin.scope.periodKey, UUID.fromString(pin.linkId)))
                }
            } catch (failure: Failure) {
                if (failure.error.refusesItem) null else throw failure
            }
            mutate(generation) { data ->
                val next = if (data.localPins[pin.scope.key] == pin) data.copy(localPins = data.localPins - pin.scope.key) else data
                answer?.let { next.withResponse(pin.scope, it) } ?: next
            }
        }
    }

    private suspend fun fetch(generation: Long, scope: ResourceScope) {
        val answer = request(generation) { api.subjectLinks(scope.subjectId, scope.periodKey) }
        mutate(generation) { it.withResponse(scope, answer) }
    }

    private suspend fun linkAction(scope: ResourceScope, block: suspend () -> ApiResponse<WireLink>): AppResult<Unit> = attempt {
        val generation = epoch.get()
        load(generation)
        networkLock.withLock {
            val answer = restricted(generation) { request(generation, block) }
            mutate(generation) { it.withLink(scope, answer) }
        }
    }

    /** A moderation restriction refreshes the capabilities; the original error is what the caller sees. */
    private suspend fun <T> restricted(generation: Long, block: suspend () -> T): T = try {
        block()
    } catch (failure: Failure) {
        if (failure.error == AppError.Restricted) {
            try { fetchRestrictions(generation) } catch (_: Failure) { /* Keep the action error. */ }
        }
        throw failure
    }

    private suspend fun fetchRestrictions(generation: Long) {
        val rows = request(generation) { api.myRestrictions() }.map { it.toModel() }
        stateLock.withLock {
            checkSession(generation)
            restrictions.value = rows
        }
    }

    private suspend fun <T> request(generation: Long, block: suspend () -> ApiResponse<T>): T =
        call(generation, block) ?: throw Failure(AppError.Unknown())

    /** Null only for answers without data, like a deletion. */
    private suspend fun <T> call(generation: Long, block: suspend () -> ApiResponse<T>): T? {
        requireEnabled(generation)
        try {
            val response = coroutineScope {
                val call = async(Dispatchers.IO, start = CoroutineStart.LAZY) { checkSession(generation); block() }
                stateLock.withLock { checkSession(generation); activeRequests.add(call) }
                try { call.await() } finally { activeRequests.remove(call) }
            }
            requireEnabled(generation)
            if (!response.success) throw Failure(when (response.error?.code) {
                "restricted" -> AppError.Restricted
                "permission_denied" -> AppError.Forbidden
                "not_found" -> AppError.NotFound
                else -> AppError.Unknown()
            })
            return response.data
        } catch (cancel: CancellationException) {
            checkSession(generation)
            throw cancel
        } catch (failure: Failure) {
            throw failure
        } catch (failure: Exception) {
            checkSession(generation)
            throw Failure(failure.toAppError())
        }
    }

    private suspend fun isEnabled(): Boolean = services.isEnabled().also { enabled = it }

    private suspend fun requireEnabled(generation: Long) {
        checkSession(generation)
        if (!isEnabled()) throw Failure(AppError.CustomServicesDisabled)
    }

    private fun checkSession(generation: Long) {
        if (clearing || generation != epoch.get()) throw Failure(AppError.Unauthorized)
    }

    private suspend fun load(generation: Long) = stateLock.withLock {
        checkSession(generation)
        if (state.value != null) return@withLock
        val saved = try {
            withContext(Dispatchers.IO) { storage.read() }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            loadError.value = AppError.Unknown(failure)
            throw Failure(AppError.Unknown(failure))
        }
        checkSession(generation)
        loadError.value = null
        state.value = saved
    }

    private suspend fun mutate(generation: Long, transform: (StoredLinks) -> StoredLinks) = stateLock.withLock {
        checkSession(generation)
        val next = transform(current())
        withContext(Dispatchers.IO) { storage.write(next) }
        state.value = next
    }

    private fun current(): StoredLinks = checkNotNull(state.value)

    private suspend fun <T> attempt(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (failure: Exception) {
        AppResult.Failure(failure.appError())
    }

    private fun snapshot(data: StoredLinks, scope: ResourceScope, on: Boolean, failed: Boolean): SubjectLinksSnapshot? {
        val local = data.local.values.filter { it.scope.key == scope.key }.sortedByDescending { it.updatedAt }.map { it.toModel() }
        val localPin = data.localPins[scope.key]?.linkId
        if (!on) return SubjectLinksSnapshot(local, emptyList(), emptyList(), localPin, emptyList(), premoderation = true, servicesEnabled = false)
        // Device-only links stay reachable when the first load of a scope fails.
        val cached = data.scopes[scope.key]?.response ?: return if (failed && local.isNotEmpty()) {
            SubjectLinksSnapshot(local, emptyList(), emptyList(), localPin, emptyList(), premoderation = true, servicesEnabled = true)
        } else null
        val mine = cached.mine.map { it.toModel() }
        val ids = mine.map { it.id }.toSet()
        return SubjectLinksSnapshot(mine + local.filter { it.id !in ids }, cached.shared.map { it.toModel() },
            cached.previous.map { it.toModel() }, cached.pinnedId?.toString(), cached.audiences.map { it.toModel() },
            cached.premoderation, servicesEnabled = true)
    }

    private fun saveRequest(
        scope: ResourceScope,
        id: String,
        category: LinkCategory,
        url: String,
        title: String?,
        visibility: LinkVisibility,
        flowId: Long?,
    ): SaveSubjectLinkRequest {
        UUID.fromString(id)
        val cleanUrl = url.trim()
        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        val uri = URI(cleanUrl)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null)
        require(cleanUrl.length <= MAX_URL_LENGTH && (cleanTitle?.length ?: 0) <= MAX_TITLE_LENGTH)
        require(scope.subjectId > 0 && scope.subjectName.isNotBlank() && scope.periodKey.matches(PERIOD_KEY))
        require((visibility == LinkVisibility.FLOW) == (flowId != null))
        return SaveSubjectLinkRequest(scope.subjectId, scope.subjectName.trim(), scope.periodKey, category.toWire(),
            cleanUrl, cleanTitle, visibility.toWire(), flowId)
    }

    private fun StoredLinks.withResponse(scope: ResourceScope, response: SubjectLinksResponse) =
        copy(scopes = scopes + (scope.key to CachedLinks(scope, response)))

    private fun StoredLinks.withMine(scope: ResourceScope, link: WireLink): StoredLinks {
        val cached = scopes[scope.key]?.response ?: return this
        val mine = if (cached.mine.any { it.id == link.id }) cached.mine.replacing(link) else cached.mine + link
        return withResponse(scope, cached.copy(mine = mine))
    }

    private fun StoredLinks.withoutMine(scope: ResourceScope, id: String): StoredLinks {
        val pins = localPins.filterValues { it.linkId != id }
        val cached = scopes[scope.key]?.response ?: return copy(localPins = pins)
        return copy(localPins = pins).withResponse(scope, cached.copy(mine = cached.mine.filterNot { it.id.toString() == id },
            pinnedId = cached.pinnedId?.takeUnless { it.toString() == id }))
    }

    private fun StoredLinks.withLink(scope: ResourceScope, link: WireLink): StoredLinks {
        val cached = scopes[scope.key]?.response ?: return this
        return withResponse(scope, cached.copy(mine = cached.mine.replacing(link), shared = cached.shared.replacing(link),
            previous = cached.previous.replacing(link)))
    }

    private fun List<WireLink>.replacing(link: WireLink) = map { if (it.id == link.id) link else it }

    /** The server refused this one link; the others are still worth sending. */
    private val AppError.refusesItem: Boolean
        get() = this == AppError.Forbidden || this == AppError.NotFound || this is AppError.Unknown

    private fun Exception.appError(): AppError = (this as? Failure)?.error ?: toAppError()

    private class Failure(val error: AppError) : RuntimeException()

    private companion object {
        const val MAX_URL_LENGTH = 2000
        const val MAX_TITLE_LENGTH = 120
        const val RESTRICTION_TICK_MILLIS = 60_000L
        val PERIOD_KEY = Regex("[0-9]{4}-[12]")
    }
}
