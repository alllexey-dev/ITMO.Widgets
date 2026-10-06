package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmoapi.bars.model.Term
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.of
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** What a background read of the own BARS journals brought. */
sealed interface BarsMarkRead {
    /** Every journal answered; [skipped] plans the mapper rejects (a course project, an unknown shape). */
    data class Journals(val plans: List<BarsPlanMarks>, val skipped: Int) : BarsMarkRead

    /** The user never signed in to BARS with this account or signed out. */
    data object NoSession : BarsMarkRead

    /** The ITMO.ID session behind the cookies ended; only an interactive sign-in brings BARS back. */
    data object SessionEnded : BarsMarkRead

    data class Failure(val error: AppError) : BarsMarkRead
}

interface BarsMarkSource {
    suspend fun read(half: StudyHalf): BarsMarkRead
}

/**
 * Reads the own journals of [StudyHalf] in the background, renewing the session through ITMO.ID cookies. The period
 * selection is shared with the BARS website, so the one the user had is selected again afterwards; a failure to do so
 * does not change the result. Any failed request fails the whole read: a partial answer is never a snapshot. A network
 * failure of any request (the period, the disciplines, any journal) is [BarsMarkRead.Failure] with [AppError.Network].
 */
class BarsMarkReader @Inject constructor(private val client: BarsClient) : BarsMarkSource {
    private val mapper = BarsRecordbookMapper()

    override suspend fun read(half: StudyHalf): BarsMarkRead = when (val result = client.backgroundAccount { journals(half) }) {
        is BarsBackground.Success -> result.value
        BarsBackground.NoSession -> BarsMarkRead.NoSession
        BarsBackground.SessionEnded -> BarsMarkRead.SessionEnded
        is BarsBackground.Failure -> BarsMarkRead.Failure(result.error)
    }

    private suspend fun BarsClient.Account.journals(half: StudyHalf): BarsMarkRead.Journals {
        val previousYear = user.selectedYear
        val previousAutumn = user.selectedTerm == Term.AUTUMN.wireValue
        val autumn = half.half == 1
        val read = try {
            selectPeriod(half.studyYear, autumn)
            Result.success(plans(journalReferences(half.yearStart, half.half)))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            Result.failure(failure)
        }
        if (STUDY_YEAR.matches(previousYear) &&
            (previousYear != half.studyYear || previousAutumn != autumn)
        ) {
            try {
                selectPeriod(previousYear, previousAutumn)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // The user's selection stays on this half until they change it; the marks are read.
            }
        }
        return read.getOrThrow()
    }

    private suspend fun BarsClient.Account.plans(references: List<BarsJournalReference>): BarsMarkRead.Journals {
        val plans = coroutineScope {
            references.map { reference ->
                async {
                    val journal = execute { getStudentJournal(reference.planId, reference.type, reference.identifier) }
                    try {
                        val subject = mapper.subject(journal, reference, owner.toString())
                        BarsPlanMarks.of(subject, mapper.controls(journal, reference, owner.toString()))
                    } catch (_: IllegalArgumentException) {
                        null
                    } catch (_: IllegalStateException) {
                        null
                    } catch (_: NullPointerException) {
                        null
                    }
                }
            }.awaitAll()
        }
        return BarsMarkRead.Journals(plans.filterNotNull(), skipped = plans.count { it == null })
    }

    private companion object {
        val STUDY_YEAR = Regex("""\d{4}/\d{4}""")
    }
}
