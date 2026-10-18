package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.feature.settings.domain.BarsConnection
import dev.alllexey.itmowidgets.feature.settings.domain.BarsStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * BARS from the mark sources: `bars_marks_enabled` is set with the account's first BARS answer and cleared with the
 * BARS session on sign-out; the sign-in prompt is pending or shown while the ITMO.ID session behind BARS has ended.
 */
class BarsStatusRepositoryImpl(private val markSources: MarkSourcePreferences) : BarsStatusRepository {
    override fun observe(): Flow<BarsConnection> = combine(
        markSources.observeBarsMarksEnabled(),
        markSources.observeBarsLoginPrompt(),
    ) { enabled, prompt ->
        when {
            prompt != BarsLoginPrompt.NONE -> BarsConnection.EXPIRED
            enabled != null -> BarsConnection.CONNECTED
            else -> BarsConnection.NOT_CONNECTED
        }
    }
}
