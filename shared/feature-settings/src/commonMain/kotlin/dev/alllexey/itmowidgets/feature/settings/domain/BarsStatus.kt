package dev.alllexey.itmowidgets.feature.settings.domain

import kotlinx.coroutines.flow.Flow

/**
 * BARS behind the recordbook: it never answered for this account, it answers, or the background check found the
 * ITMO.ID session behind it ended (`BarsLoginPrompt`).
 */
enum class BarsConnection { NOT_CONNECTED, CONNECTED, EXPIRED }

/** The state of BARS that «Подключение и данные» shows. */
interface BarsStatusRepository {
    fun observe(): Flow<BarsConnection>
}
