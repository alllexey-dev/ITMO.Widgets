import Shared
import SwiftUI

/// My sections (`AppRoutes.SportSections`) over the shared `SportSectionsViewModel`, pushed on the sport stack under
/// the native bar: the queue limit as the subtitle, `+` to pin a section, each pinned section with its next lessons
/// and the every-other-week queue (hidden while Backend keeps no repeat rules), the limit's gate when it is spent.
struct SportSectionsScreen: View {
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss
    @State private var model = ObservableViewModel(SportSectionsViewModel.self, state: \.uiState)
    @State private var lesson: SportLessonItem?
    @State private var message: SettingsMessage?

    var body: some View {
        SportSectionsContent(state: model.state, actions: actions)
            .refreshable { model.viewModel.refresh(mode: .pull) }
            .navigationTitle(Text(verbatim: AppStrings.string("sport_sections_title")))
            .navigationSubtitle(Text(verbatim: model.state.subtitle?.resolved ?? ""))
            .toolbarTitleDisplayMode(.inlineLarge)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { model.viewModel.openPicker() } label: { AppSymbol.add.image }
                        .accessibilityLabel(Text(verbatim: AppStrings.string("sport_sections_add")))
                        .accessibilityIdentifier("sport.sections.add")
                }
            }
            .sheet(isPresented: pickerShown) {
                if let options = model.state.picker {
                    SportSectionsPicker(options: options) { model.viewModel.togglePinned(name: $0) }
                }
            }
            .sheet(item: $lesson) { item in
                let viewModel = model.viewModel
                SportLessonSheet(item: item, present: { viewModel.sheet(details: item.details, submitted: $0) }) { action in
                    lesson = nil
                    viewModel.onSheetAction(lessonId: item.details.lessonId, action: action)
                } openProfile: { isu in
                    lesson = nil
                    router.open(AppRoutes.UserProfile(isu: isu))
                }
            }
            .sheet(isPresented: joinShown) {
                if let join = model.state.prompt as? SportPromptJoin {
                    SportJoinSheet(join: join) { model.viewModel.confirmPrompt(lateSign: $0) }
                }
            }
            .sportPromptAlert(model.state.prompt, confirm: { model.viewModel.confirmPrompt(lateSign: false) }) {
                model.viewModel.dismissPrompt()
            }
            .overlay(alignment: .bottom) {
                if let message {
                    SettingsMessageBanner(text: message.text)
                        .task(id: message.id) {
                            try? await Task.sleep(for: .seconds(3))
                            if self.message?.id == message.id { self.message = nil }
                        }
                }
            }
            .observing(model)
            .onEvents(of: model, \.events) { text in
                message = SettingsMessage(text: text.resolved)
                AccessibilityNotification.Announcement(text.resolved).post()
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("sport.sectionsScreen")
    }

    private var actions: SportSectionsActions {
        let viewModel = model.viewModel
        return SportSectionsActions(
            pick: { viewModel.openPicker() },
            unpin: { viewModel.togglePinned(name: $0) },
            repeat: { viewModel.toggleRepeat(name: $0) },
            open: { id in
                if let details = viewModel.details(lessonId: id) { lesson = SportLessonItem(details: details, from: .sign) }
            },
            act: { viewModel.act(lessonId: $0) },
            bookings: { dismiss() },
            retry: { viewModel.refresh(mode: .force) }
        )
    }

    private var pickerShown: Binding<Bool> {
        Binding(get: { model.state.picker != nil }, set: { if !$0 { model.viewModel.closePicker() } })
    }

    private var joinShown: Binding<Bool> {
        Binding(get: { model.state.prompt is SportPromptJoin }, set: { if !$0 { model.viewModel.dismissPrompt() } })
    }
}

/// What My sections asks its host to do.
struct SportSectionsActions {
    var pick: () -> Void = {}
    var unpin: (String) -> Void = { _ in }
    var `repeat`: (String) -> Void = { _ in }
    var open: (Int64) -> Void = { _ in }
    var act: (Int64) -> Void = { _ in }
    var bookings: () -> Void = {}
    var retry: () -> Void = {}
}

/// My sections without the ViewModel.
struct SportSectionsContent: View {
    let state: SportSectionsUiState
    var actions = SportSectionsActions()

    var body: some View {
        ItmoScreenStack {
            switch onEnum(of: state.body) {
            case .loading:
                ItmoLoadingView()
            case let .failed(failed):
                ItmoErrorView(
                    title: AppStrings.string("common_load_error_title"),
                    description: AppErrorTextsKt.toUiText(failed.error).resolved,
                    retry: actions.retry
                )
            case .empty:
                ItmoEmptyView(
                    symbol: .keep,
                    title: AppStrings.string("sport_sections_empty_title"),
                    description: AppStrings.string("sport_sections_empty_text"),
                    action: (AppStrings.string("sport_sections_pick"), actions.pick)
                )
                .accessibilityIdentifier("sport.sections.empty")
            case let .content(content):
                if let gate = state.limitGate {
                    ItmoGateCard(
                        title: gate.title.resolved,
                        reason: gate.reason.resolved,
                        fix: (gate.fix.resolved, actions.bookings),
                        symbol: .hourglassTop
                    )
                }
                ForEach(content.sections, id: \.name) { SportSectionCardView(card: $0, actions: actions) }
                if let note = content.note {
                    ItmoGroupNote(text: note.resolved)
                }
            }
        }
    }
}

/// A pinned section as one group: its header, its next lessons, the repeat switch and its dates.
struct SportSectionCardView: View {
    let card: SportSectionCard
    let actions: SportSectionsActions

    var body: some View {
        ItmoListGroup {
            HStack(spacing: ItmoSpacing.content) {
                ItmoIconTile(symbol: .sprint, tone: .primary, size: 44)
                VStack(alignment: .leading, spacing: 1) {
                    Text(verbatim: card.title).font(.itmo(.titleLarge, emphasized: true))
                    if let teacher = card.teacher {
                        Text(verbatim: teacher).font(.itmo(.bodyMedium)).foregroundStyle(.secondary).lineLimit(1)
                    }
                    SportKindTag(kind: card.kind?.resolved, tone: card.kindTone, place: card.place?.resolved)
                }
                Spacer(minLength: 0)
                Button { actions.unpin(card.name) } label: { AppSymbol.keep.image.foregroundStyle(ItmoColor.primary) }
                    .buttonStyle(.borderless)
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityLabel(Text(verbatim: AppStrings.string("sport_sections_unpin")))
            }
            .padding(ItmoKit.padding)
            ForEach(card.lessons, id: \.lessonId) { lesson in
                HStack(spacing: ItmoSpacing.compact) {
                    Button { actions.open(lesson.lessonId) } label: {
                        HStack(spacing: ItmoSpacing.content) {
                            Text(verbatim: lesson.day.resolved).font(.itmo(.bodyMedium))
                            Text(verbatim: lesson.time).font(.itmo(.bodyMedium, emphasized: true)).monospacedDigit()
                            Spacer(minLength: 0)
                        }
                        .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                    SportRowActionView(action: lesson.action) { actions.act(lesson.lessonId) }
                }
                .padding(.horizontal, ItmoKit.padding)
                .frame(minHeight: 44)
            }
            if let repeatUi = card.repeat {
                ItmoSwitchRow(
                    title: AppStrings.string("sport_repeat_title"),
                    subtitle: repeatUi.subtitle.resolved,
                    symbol: .eventRepeat,
                    enabled: repeatUi.enabled,
                    isOn: Binding(get: { repeatUi.on }, set: { _ in actions.repeat(card.name) })
                )
                .accessibilityIdentifier("sport.sections.repeat")
                if !repeatUi.dates.isEmpty || repeatUi.clash != nil {
                    VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
                        if !repeatUi.dates.isEmpty {
                            Text(verbatim: AppStrings.string("sport_repeat_next")).font(.itmo(.bodySmall)).foregroundStyle(.secondary)
                            ItmoFlow { ForEach(repeatUi.dates.map(\.resolved), id: \.self) { ItmoStatusPill(pill: ItmoPill(text: $0)) } }
                        }
                        if let clash = repeatUi.clash {
                            Label { Text(verbatim: clash.resolved) } icon: {
                                AppSymbol.eventBusy.image.foregroundStyle(ItmoTone.warn.color)
                            }
                            .font(.itmo(.bodyMedium))
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, ItmoKit.padding)
                    .padding(.vertical, 11)
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("sport.sections.card")
    }
}

/// Every section of the catalog; a tap pins or unpins it.
struct SportSectionsPicker: View {
    let options: [SportOption]
    let toggle: (String) -> Void

    var body: some View {
        ItmoSheet(title: AppStrings.string("sport_sections_picker_title"), height: .tall) {
            ItmoListGroup(inset: ItmoKit.padding) {
                ForEach(options, id: \.self) { option in
                    ItmoRow(title: option.label.resolved, action: { option.value.map(toggle) })
                        .accessory { if option.selected { AppSymbol.check.image.foregroundStyle(ItmoColor.primary) } }
                }
            }
        }
        .accessibilityIdentifier("sport.sections.picker")
    }
}
