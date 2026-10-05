package dev.alllexey.itmowidgets.feature.settings.presentation

import javax.inject.Inject

/** Every settings page provider, listed once; the ViewModel finds a page's builder and a row's handler here. */
class SettingsPages @Inject constructor(
    root: RootPageProvider,
    services: ServicesPageProvider,
    val widgets: WidgetsPageProvider,
    home: HomePageProvider,
    val schedule: SchedulePageProvider,
    recordbook: RecordbookPageProvider,
    sport: SportPageProvider,
    maintenance: MaintenancePageProvider
) {

    val providers: List<SettingsPageProvider> =
        listOf(root, services, widgets, home, schedule, recordbook, sport, maintenance)

    private val byPage = providers.flatMap { provider -> provider.pages.map { it to provider } }.toMap()
    private val byRow = providers.flatMap { provider -> provider.rows.map { it to provider } }.toMap()

    fun forPage(page: SettingsPage): SettingsPageProvider =
        byPage[page] ?: throw IllegalStateException("No settings provider builds $page")

    /** The provider that handles [id]; null for navigation rows, which the screen opens itself. */
    fun forRow(id: SettingRowId): SettingsPageProvider? = byRow[id]
}
