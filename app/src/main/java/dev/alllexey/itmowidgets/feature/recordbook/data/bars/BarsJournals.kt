package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference

/**
 * The own journals of the period selected on the server. A student's marks are plan-scoped; lecture and practice
 * flows can reference the same plan, so every plan is read once through its first flow.
 */
internal suspend fun BarsClient.Account.journalReferences(yearStart: Int, semester: Int): List<BarsJournalReference> {
    val disciplines = execute { api.getDisciplines(true) }
    val groups = execute { api.getGroupsAndFlows(null) }
    return disciplines.flatMap { it.checkpointPlanIds }.distinct().mapNotNull { plan ->
        val group = groups.firstOrNull { plan in it.checkpointPlanIds } ?: return@mapNotNull null
        BarsJournalReference(plan, group.type, group.identifier, yearStart, semester)
    }
}
