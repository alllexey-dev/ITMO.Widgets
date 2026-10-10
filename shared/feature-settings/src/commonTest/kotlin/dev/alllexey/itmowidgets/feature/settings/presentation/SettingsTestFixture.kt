package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeMarkTracking
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeScheduleChangeTracking
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Builds the settings ViewModel over fakes for the page provider tests: each test drives a page through the
 * ViewModel, so a provider is checked together with the scope it gets.
 */
internal fun createFixture(
    local: LocalSettings = LocalSettings(),
    sharing: SharingSettingsState = SharingSettingsState.Disabled,
    localInitiallyAvailable: Boolean = true,
    page: SettingsPage = SettingsPage.ROOT,
    backgroundWork: FakeBackgroundWorkAccess = FakeBackgroundWorkAccess(),
    tileAccess: FakeQuickSettingsTileAccess = FakeQuickSettingsTileAccess(),
    calendarSync: FakeCalendarSync = FakeCalendarSync(),
    capabilities: PlatformCapabilities = EveryPlatformCapability
): Fixture {
    val tracking = FakeScheduleChangeTracking(enabled = local.scheduleChangesEnabled)
    val markTracking = FakeMarkTracking()
    val repository = FakeSettingsRepository(local, sharing, localInitiallyAvailable, tracking.enabled)
    val customServicesRepository = FakeCustomServicesRepository { enabled ->
        repository.local.value = repository.local.value.copy(
            customServicesEnabled = enabled
        )
    }
    val refresher = FakeWidgetRefreshRequester()
    val onboarding = FakeOnboardingRepository(completed = true)
    val pages = SettingsPages(
        root = RootPageProvider(repository, capabilities),
        services = ServicesPageProvider(repository, customServicesRepository, refresher),
        widgets = WidgetsPageProvider(repository, tileAccess, capabilities),
        home = HomePageProvider(repository),
        schedule = SchedulePageProvider(repository, tracking, calendarSync, capabilities),
        recordbook = RecordbookPageProvider(markTracking),
        sport = SportPageProvider(repository),
        maintenance = MaintenancePageProvider(refresher, onboarding, AppVersion("2.1-test"), RecordingDiagnostics())
    )
    val viewModel = SettingsViewModel(
        pages = pages,
        repository = repository,
        widgetRefreshRequester = refresher,
        backgroundWork = backgroundWork,
        savedStateHandle = SavedStateHandle(mapOf(SettingsPage.ARGUMENT to page.name))
    )
    return Fixture(
        viewModel, pages, repository, customServicesRepository, refresher, onboarding, tracking, markTracking, backgroundWork,
        tileAccess, calendarSync
    )
}

/** Collects every event from now on; the channel has one receiver, so a test uses either this or `events.first()`. */
internal fun TestScope.recordEvents(fixture: Fixture): List<SettingsEvent> {
    val events = mutableListOf<SettingsEvent>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.viewModel.events.toList(events) }
    return events
}

internal fun SettingsViewModel.allItems(): List<SettingItem> =
    uiState.value.sections.flatMap(SettingSection::items)

internal fun SettingsViewModel.toggle(id: SettingRowId): SettingItem.Toggle =
    allItems().filterIsInstance<SettingItem.Toggle>().single { it.id == id }

internal fun SettingsViewModel.choice(id: SettingRowId): SettingItem.Choice =
    allItems().filterIsInstance<SettingItem.Choice>().single { it.id == id }

internal fun SettingsViewModel.action(id: SettingRowId): SettingItem.Action =
    allItems().filterIsInstance<SettingItem.Action>().single { it.id == id }

internal data class Fixture(
    val viewModel: SettingsViewModel,
    val pages: SettingsPages,
    val repository: FakeSettingsRepository,
    val customServicesRepository: FakeCustomServicesRepository,
    val widgetRefresher: FakeWidgetRefreshRequester,
    val onboardingRepository: FakeOnboardingRepository,
    val tracking: FakeScheduleChangeTracking,
    val markTracking: FakeMarkTracking,
    val backgroundWork: FakeBackgroundWorkAccess,
    val tileAccess: FakeQuickSettingsTileAccess,
    val calendarSync: FakeCalendarSync
)


/** Unrestricted by default, so the background work row stays out of the other cases. */
internal class FakeBackgroundWorkAccess(var unrestricted: Boolean = true) : BackgroundWorkAccess {
    override fun isUnrestricted(): Boolean = unrestricted
}

/** Below Android 13 by default, so the tile row stays out of the other cases. */
internal class FakeQuickSettingsTileAccess(var canRequest: Boolean = false) : QuickSettingsTileAccess {
    override fun canRequestAdd(): Boolean = canRequest
}

internal class FakeSettingsRepository(
    initialLocal: LocalSettings,
    initialSharing: SharingSettingsState,
    localInitiallyAvailable: Boolean,
    /** The switch lives behind [dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking]; reads follow it. */
    private val scheduleChangesEnabled: MutableStateFlow<Boolean>
) : SettingsRepository {

    val local = MutableStateFlow(initialLocal)
    /** The mark switches live behind [dev.alllexey.itmowidgets.core.recordbook.MarkTracking]; tests move them here. */
    val myItmoMarks = MutableStateFlow(initialLocal.myItmoMarksEnabled)
    val barsMarks = MutableStateFlow(initialLocal.barsMarksEnabled)
    val sheetMarks = MutableStateFlow(initialLocal.sheetMarksEnabled)
    val backgroundWorkHintShown = MutableStateFlow(initialLocal.backgroundWorkHintShown)
    var hintShownCalls = 0
    val qrTileAdded = MutableStateFlow(initialLocal.qrTileAdded)
    val qrTileAddedRequests = mutableListOf<Boolean>()
    val accentColorRequests = mutableListOf<AccentColor>()
    private val localAvailable = MutableStateFlow(localInitiallyAvailable)
    val sharing = MutableStateFlow(initialSharing)
    var refreshSharingCount = 0
    var disableSharingCount = 0
    var scheduleSharingResult: AppResult<Unit> = AppResult.Success(Unit)
    var sportSharingResult: AppResult<Unit> = AppResult.Success(Unit)
    var sharingWrite: suspend () -> Unit = {}
    val scheduleSharingRequests = mutableListOf<SharingVisibility>()
    val sportSharingRequests = mutableListOf<SharingVisibility>()
    val friendsSharingRequests = mutableListOf<SharingVisibility>()
    val nextLessonEarlyRequests = mutableListOf<Boolean>()
    val widgetTeacherHiddenRequests = mutableListOf<Boolean>()
    val pastLessonsHiddenRequests = mutableListOf<Boolean>()
    val tomorrowScheduleRequests = mutableListOf<Boolean>()
    val qrDynamicColorsRequests = mutableListOf<Boolean>()
    val qrSpoilerRequests = mutableListOf<Boolean>()
    val qrAnimationRequests = mutableListOf<QrAnimationType>()
    val teacherSelectorHiddenRequests = mutableListOf<Boolean>()
    val timeSelectorHiddenRequests = mutableListOf<Boolean>()
    val scheduleSportAutoSignRequests = mutableListOf<Boolean>()
    var scheduleSportAutoSignWrite: suspend () -> Unit = {}

    override fun observeLocalSettings(): Flow<LocalSettings> =
        combine(
            localAvailable,
            combine(local, backgroundWorkHintShown, sheetMarks, qrTileAdded) { settings, hintShown, sheets, tileAdded ->
                settings.copy(backgroundWorkHintShown = hintShown, sheetMarksEnabled = sheets, qrTileAdded = tileAdded)
            },
            scheduleChangesEnabled,
            myItmoMarks,
            barsMarks
        ) { available, settings, scheduleChanges, myItmo, bars ->
            settings.copy(scheduleChangesEnabled = scheduleChanges, myItmoMarksEnabled = myItmo, barsMarksEnabled = bars)
                .takeIf { available }
        }.filterNotNull()

    fun publishLocalSettings() {
        localAvailable.value = true
    }

    override fun observeSharingSettings(): Flow<SharingSettingsState> = sharing

    var refreshDelayMs = 0L

    override suspend fun refreshSharingSettings() {
        refreshSharingCount += 1
        delay(refreshDelayMs)
    }

    override fun disableSharingSettings() {
        disableSharingCount += 1
        sharing.value = SharingSettingsState.Disabled
    }

    override suspend fun setScheduleVisibility(visibility: SharingVisibility): AppResult<Unit> {
        scheduleSharingRequests += visibility
        sharingWrite()
        if (scheduleSharingResult is AppResult.Success) {
            val content = sharing.value as SharingSettingsState.Content
            sharing.value = content.copy(settings = content.settings.copy(scheduleVisibility = visibility))
        }
        return scheduleSharingResult
    }

    override suspend fun setFriendsVisibility(visibility: SharingVisibility): AppResult<Unit> {
        friendsSharingRequests += visibility
        sharingWrite()
        if (sportSharingResult is AppResult.Success) {
            val content = sharing.value as SharingSettingsState.Content
            sharing.value = content.copy(settings = content.settings.copy(friendsVisibility = visibility))
        }
        return sportSharingResult
    }

    override suspend fun setSportVisibility(visibility: SharingVisibility): AppResult<Unit> {
        sportSharingRequests += visibility
        sharingWrite()
        if (sportSharingResult is AppResult.Success) {
            val content = sharing.value as SharingSettingsState.Content
            sharing.value = content.copy(settings = content.settings.copy(sportVisibility = visibility))
        }
        return sportSharingResult
    }

    override suspend fun setCompactWidgetNextLessonEarlyEnabled(enabled: Boolean) {
        nextLessonEarlyRequests += enabled
        local.value = local.value.copy(
            scheduleWidget = local.value.scheduleWidget.copy(compact = local.value.scheduleWidget.compact.copy(showNextLessonEarly = enabled))
        )
    }

    override suspend fun setCompactWidgetTeacherHidden(hidden: Boolean) {
        widgetTeacherHiddenRequests += hidden
        local.value = local.value.copy(
            scheduleWidget = local.value.scheduleWidget.copy(compact = local.value.scheduleWidget.compact.copy(hideTeacher = hidden))
        )
    }

    val fullWidgetTeacherHiddenRequests = mutableListOf<Boolean>()

    override suspend fun setFullWidgetTeacherHidden(hidden: Boolean) {
        fullWidgetTeacherHiddenRequests += hidden
        local.value = local.value.copy(scheduleWidget = local.value.scheduleWidget.copy(
            full = local.value.scheduleWidget.full.copy(hideTeacher = hidden)
        ))
    }

    override suspend fun setFullWidgetPastLessonsHidden(hidden: Boolean) {
        pastLessonsHiddenRequests += hidden
        local.value = local.value.copy(
            scheduleWidget = local.value.scheduleWidget.copy(full = local.value.scheduleWidget.full.copy(hidePastLessons = hidden))
        )
    }

    override suspend fun setFullWidgetTomorrowEnabled(enabled: Boolean) {
        tomorrowScheduleRequests += enabled
        local.value = local.value.copy(
            scheduleWidget = local.value.scheduleWidget.copy(full = local.value.scheduleWidget.full.copy(
                showTomorrowWhenTodayIsOver = enabled
            ))
        )
    }

    val compactTextSizeRequests = mutableListOf<WidgetTextSize>()
    val fullTextSizeRequests = mutableListOf<WidgetTextSize>()

    override suspend fun setCompactWidgetTextSize(size: WidgetTextSize) {
        compactTextSizeRequests += size
        local.value = local.value.copy(
            scheduleWidget = local.value.scheduleWidget.copy(
                compact = local.value.scheduleWidget.compact.copy(textSize = size)
            )
        )
    }

    override suspend fun setFullWidgetTextSize(size: WidgetTextSize) {
        fullTextSizeRequests += size
        local.value = local.value.copy(
            scheduleWidget = local.value.scheduleWidget.copy(
                full = local.value.scheduleWidget.full.copy(textSize = size)
            )
        )
    }

    override suspend fun setQrDynamicColorsEnabled(enabled: Boolean) {
        qrDynamicColorsRequests += enabled
        local.value = local.value.copy(
            qrWidget = local.value.qrWidget.copy(dynamicColors = enabled)
        )
    }

    override suspend fun setQrSpoilerEnabled(enabled: Boolean) {
        qrSpoilerRequests += enabled
        local.value = local.value.copy(
            qrWidget = local.value.qrWidget.copy(spoilerEnabled = enabled)
        )
    }

    override suspend fun setQrAnimationType(type: QrAnimationType) {
        qrAnimationRequests += type
        local.value = local.value.copy(
            qrWidget = local.value.qrWidget.copy(animationType = type)
        )
    }

    override suspend fun setTeacherSelectorHidden(hidden: Boolean) {
        teacherSelectorHiddenRequests += hidden
        local.value = local.value.copy(
            sport = local.value.sport.copy(hideTeacherSelector = hidden)
        )
    }

    override suspend fun setTimeSelectorHidden(hidden: Boolean) {
        timeSelectorHiddenRequests += hidden
        local.value = local.value.copy(
            sport = local.value.sport.copy(hideTimeSelector = hidden)
        )
    }

    override suspend fun setScheduleSportAutoSignEnabled(enabled: Boolean) {
        scheduleSportAutoSignRequests += enabled
        scheduleSportAutoSignWrite()
        local.value = local.value.copy(showSportAutoSign = enabled)
    }

    val homeCardRequests = mutableListOf<Pair<HomeCardKind, Boolean>>()

    override suspend fun setHomeCardVisible(kind: HomeCardKind, visible: Boolean) {
        homeCardRequests += kind to visible
        local.value = local.value.copy(hiddenHomeCards = if (visible) local.value.hiddenHomeCards - kind else local.value.hiddenHomeCards + kind)
    }

    override suspend fun setBackgroundWorkHintShown() {
        hintShownCalls += 1
        backgroundWorkHintShown.value = true
    }

    override suspend fun setQrTileAdded(added: Boolean) {
        qrTileAddedRequests += added
        qrTileAdded.value = added
    }

    override suspend fun setAccentColor(color: AccentColor) {
        accentColorRequests += color
        local.value = local.value.copy(accentColor = color)
    }
}

internal class FakeWidgetRefreshRequester : WidgetRefreshRequester {
    var refreshCount = 0

    override fun refreshAll() {
        refreshCount += 1
    }
}
