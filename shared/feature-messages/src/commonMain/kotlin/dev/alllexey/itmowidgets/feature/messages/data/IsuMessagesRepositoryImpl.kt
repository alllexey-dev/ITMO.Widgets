package dev.alllexey.itmowidgets.feature.messages.data

import com.fleeksoft.ksoup.Ksoup
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.IsuSession
import dev.alllexey.itmowidgets.core.network.IsuWebClient
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.messages.data.demo.DemoIsuMessages
import dev.alllexey.itmowidgets.feature.messages.domain.AttachmentFile
import dev.alllexey.itmowidgets.feature.messages.domain.AttachmentFiles
import dev.alllexey.itmowidgets.feature.messages.domain.IsuAttachment
import dev.alllexey.itmowidgets.feature.messages.domain.IsuFolder
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessagesPage
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessagesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The ISU messages of the signed-in user (app 2422) through the on-device [IsuWebClient] (ADR 0037):
 *
 * - the session opens the inbox page `2422:4` once, as a browser does; its hidden `USER_ID`, when present, must be
 *   the ISU number of the ITMO.ID token ([CurrentUserProvider]), or the session is refused;
 * - a page of a folder is the on-demand process `MAIN_FN`: page 4 for the inbox, page 8 with `x03=1` for the
 *   archive; `x01` is always the token's ISU number, `x02` the page;
 * - an attachment is `f?p=2422:0::DWNLD_F_GEN:NO::FILE:<hash>`.
 *
 * The request that opens one message (page 3) is never sent: it marks the message read in ISU. One session is kept
 * in memory for the token's ISU number; a session ISU lost is signed in again once; sign-out drops it with the saved
 * attachment. The demo session reads [DemoIsuMessages] and never reaches ISU. Nothing read leaves the device.
 */
class IsuMessagesRepositoryImpl(
    private val isu: IsuWebClient,
    private val users: CurrentUserProvider,
    private val files: AttachmentFiles,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : IsuMessagesRepository, SessionDataCleaner {

    private val lock = Mutex()
    private var session: Owned? = null

    override suspend fun page(folder: IsuFolder, number: Int): AppResult<IsuMessagesPage> {
        if (demo.isActive()) return AppResult.Success(DemoIsuMessages.page(folder, number))
        return withSession { session, owner ->
            val arguments = listOfNotNull(owner.toString(), number.toString(), "1".takeIf { folder == IsuFolder.ARCHIVE })
            session.process(APP, if (folder == IsuFolder.INBOX) INBOX_PAGE else ARCHIVE_PAGE, LIST_PROCESS, arguments)
                ?.let(IsuMessagesJson::parse)
        }
    }

    override suspend fun download(attachment: IsuAttachment): AppResult<AttachmentFile> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        if (!IsuMessagesJson.isFileHash(attachment.id)) return AppResult.Failure(AppError.NotFound)
        val bytes = when (val result = withSession { session, _ -> session.download("f?p=$APP:0::DWNLD_F_GEN:NO::FILE:${attachment.id}", MAX_FILE_BYTES) }) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }
        val mime = attachment.mimeType ?: DEFAULT_MIME
        return guarded { AttachmentFile(files.save(AttachmentNames.safe(attachment.name), mime, bytes), mime) }
    }

    override suspend fun clearSessionData() {
        lock.withLock { session = null }
        files.clear()
    }

    /**
     * Runs [request] in the session of the token's ISU number; a null answer means ISU lost the session, which is
     * signed in again once.
     */
    private suspend fun <T : Any> withSession(request: suspend (IsuSession, Int) -> T?): AppResult<T> {
        val owner = users.getCurrentUser()?.isu ?: return AppResult.Failure(AppError.Unauthorized)
        return guarded {
            withContext(dispatchers.io) {
                request(current(owner, renew = false) ?: throw SignInNeeded(), owner)
                    ?: request(current(owner, renew = true) ?: throw SignInNeeded(), owner)
                    ?: throw SignInNeeded()
            }
        }
    }

    /** The kept session of [owner], or a new one; null when ITMO.ID wants the user. */
    private suspend fun current(owner: Int, renew: Boolean): IsuSession? = lock.withLock {
        session?.takeIf { !renew && it.owner == owner }?.let { return@withLock it.session }
        session = null
        val fresh = isu.signIn() ?: return@withLock null
        val shell = fresh.page(APP, INBOX_PAGE.toString()) ?: return@withLock null
        // Fail soft on a changed page: the list names the token's ISU number either way.
        val shown = runCatching { Ksoup.parse(shell).getElementById(USER_ID)?.attr("value")?.trim() }.getOrNull()
        if (!shown.isNullOrEmpty() && shown != owner.toString()) throw OtherAccount()
        session = Owned(owner, fresh)
        fresh
    }

    private suspend fun <T> guarded(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (failure: Exception) {
        if (failure is SignInNeeded || failure is OtherAccount) lock.withLock { session = null }
        AppResult.Failure(
            when {
                failure is SignInNeeded -> AppError.Unauthorized
                failure is OtherAccount -> AppError.Forbidden
                failure.isCausedByNetworkFailure() -> AppError.Network
                else -> AppError.Unknown()
            },
        )
    }

    private class Owned(val owner: Int, val session: IsuSession)

    /** ITMO.ID wants the user, or ISU keeps losing the session. */
    private class SignInNeeded : Exception("ISU sign-in needed")

    /** ISU's page names another account than the token. */
    private class OtherAccount : Exception("ISU shows another account")

    private companion object {
        const val APP = 2422
        const val INBOX_PAGE = 4
        const val ARCHIVE_PAGE = 8
        const val LIST_PROCESS = "MAIN_FN"
        const val USER_ID = "USER_ID"
        const val MAX_FILE_BYTES = 40 * 1024 * 1024
        const val DEFAULT_MIME = "application/octet-stream"
    }
}
