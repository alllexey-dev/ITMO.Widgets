import CoreTransferable
import Shared
import SwiftUI
import UniformTypeIdentifiers

/// `settings_ics_export_title` (IO-15b): Android's `IcsExportBottomSheet` over the shared `IcsExportViewModel`. A range is
/// chosen, `IosIcsFileExport` writes the file into the app's temporary directory, and `ShareLink` hands it on as
/// `text/calendar`; the share sheet holds Calendar, Files and the messengers, so iOS needs no separate `ics_open`
/// button. The settings calendar group opens it (`SettingsEvent.OpenIcsExport`).
struct IcsExportSheet: View {
    @State private var model: ObservableViewModel<IcsExportViewModel, IcsExportUiState>
    @State private var pickingDates = false
    @Environment(\.dismiss) private var dismiss

    init() {
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters: [Any?] = icsExportParameters()
            guard let model = store.resolve(type: IcsExportViewModel.self, parameters: parameters)
                as? IcsExportViewModel else {
                preconditionFailure("Koin resolved no IcsExportViewModel")
            }
            return model
        })
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                IcsExportContent(state: model.state, actions: actions)
            }
            // The grouped background of the settings it opens from, so the ranges read as one card.
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle(Text(verbatim: AppStrings.string("settings_ics_export_title")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        AppSymbol.close.image
                    }
                    .accessibilityLabel(Text(verbatim: AppStrings.string("common_close")))
                    .accessibilityIdentifier("ics.close")
                }
            }
        }
        .observing(model)
        .onEvents(of: model, \.events) { event in
            switch onEnum(of: event) {
            case .pickDates: pickingDates = true
            }
        }
        .sheet(isPresented: $pickingDates) {
            IcsDatesPicker { start, end in
                pickingDates = false
                pickIcsExportDates(viewModel: model.viewModel, start: IcsDay.iso(start), end: IcsDay.iso(end))
            } cancel: {
                pickingDates = false
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("settings.sheet.ics")
    }

    private var actions: IcsExportSheetActions {
        let viewModel = model.viewModel
        return IcsExportSheetActions(
            choose: { viewModel.choose(kind: $0) },
            chooseAnother: { viewModel.chooseAnother() },
            retry: { viewModel.retry() }
        )
    }
}

/// What the sheet's rows and buttons do; snapshot tests pass no-ops. Sending is the `ShareLink`'s own.
struct IcsExportSheetActions {
    var choose: (IcsRangeKind) -> Void = { _ in }
    var chooseAnother: () -> Void = {}
    var retry: () -> Void = {}
}

/// The sheet's one area: the four ranges, the file being written, the file, an empty range or a failure. Every
/// state takes at least `minHeight`, so the sheet does not jump between them. Stateless, for snapshot tests.
struct IcsExportContent: View {
    let state: IcsExportUiState
    var actions = IcsExportSheetActions()

    /// Android's `ics_export_content_min_height`.
    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 288

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.content) {
            Text(verbatim: AppStrings.string("ics_sheet_subtitle"))
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
                .padding(.horizontal, ItmoSpacing.screenMargin)
            area
                .frame(maxWidth: .infinity, minHeight: minHeight, alignment: isChoice ? .top : .center)
        }
        .padding(.vertical, ItmoSpacing.content)
    }

    private var isChoice: Bool { state is IcsExportUiStateChoose }

    @ViewBuilder private var area: some View {
        switch onEnum(of: state) {
        case let .choose(choice):
            IcsRanges(options: choice.options, choose: actions.choose)
        case .preparing:
            ItmoLoadingView(title: AppStrings.string("ics_preparing"))
                .accessibilityIdentifier("ics.preparing")
        case let .ready(ready):
            IcsReady(ready: ready)
        case .empty:
            VStack(spacing: 0) {
                ItmoEmptyView(symbol: .eventNote, title: AppStrings.string("ics_empty"))
                Button(action: actions.chooseAnother) {
                    Text(verbatim: AppStrings.string("ics_pick_other"))
                        .font(.itmo(.labelLarge))
                        .frame(minHeight: ItmoMetrics.touchTarget)
                }
                .buttonStyle(.bordered)
                .itmoTint()
                .accessibilityIdentifier("ics.pickOther")
            }
            .accessibilityIdentifier("ics.empty")
        case let .failed(failed):
            ItmoErrorView(title: AppErrorTextsKt.toUiText(failed.error).resolved, retry: actions.retry)
        }
    }
}

/// The ranges with the days each covers from today; the whole row is the target.
private struct IcsRanges: View {
    let options: [IcsRangeOption]
    let choose: (IcsRangeKind) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(options.enumerated()), id: \.offset) { index, option in
                if index > 0 {
                    Divider().padding(.leading, ItmoSpacing.cardPadding)
                }
                Button {
                    choose(option.kind)
                } label: {
                    SettingsValueLabel(
                        title: option.title.resolved,
                        subtitle: option.dates.resolved,
                        trailing: .chevronRight
                    )
                    .padding(.horizontal, ItmoSpacing.cardPadding)
                    .padding(.vertical, ItmoSpacing.content)
                    .frame(minHeight: ItmoMetrics.touchTarget)
                }
                .tint(.primary)
                .accessibilityIdentifier("ics.range.\(option.kind.identifier)")
            }
        }
        .background(
            RoundedRectangle(cornerRadius: ItmoCorner.large).fill(Color(uiColor: .secondarySystemGroupedBackground))
        )
        .padding(.horizontal, ItmoSpacing.screenMargin)
    }
}

extension IcsRangeKind {
    /// Android's test tags without the prefix: `week`, `two_weeks`, `semester`, `custom`.
    var identifier: String {
        switch self {
        case .week: "week"
        case .twoWeeks: "two_weeks"
        case .semester: "semester"
        case .custom: "custom"
        }
    }
}

/// The written file: how many pairs over which days, the share button and the advice to import it separately.
private struct IcsReady: View {
    let ready: IcsExportUiStateReady

    var body: some View {
        VStack(spacing: ItmoSpacing.content) {
            AppSymbol.eventNote.image
                .font(.itmo(.headlineMedium))
                .foregroundStyle(ItmoColor.primary)
                .accessibilityHidden(true)
            Text(verbatim: AppStrings.string("ics_ready_title"))
                .font(.itmo(.titleMedium))
                .accessibilityAddTraits(.isHeader)
            Text(verbatim: summary)
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
            if let url = URL(string: ready.file.uri) {
                ShareLink(
                    item: IcsDocument(url: url),
                    preview: SharePreview(ready.file.name, image: AppSymbol.eventNote.image)
                ) {
                    Label {
                        Text(verbatim: AppStrings.string("ics_send")).font(.itmo(.labelLarge))
                    } icon: {
                        AppSymbol.share.image.accessibilityHidden(true)
                    }
                    .foregroundStyle(ItmoColor.onPrimary)
                    .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.roundedRectangle(radius: ItmoCorner.large))
                .itmoTint()
                .padding(.top, ItmoSpacing.content)
                .accessibilityIdentifier("ics.send")
            }
            Text(verbatim: AppStrings.string("ics_ready_hint"))
                .font(.itmo(.bodySmall))
                .foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, ItmoSpacing.screenMargin)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("ics.ready")
    }

    /// `ics_ready_summary`: the plural lesson count, then the days.
    private var summary: String {
        let lessons = Int(ready.file.lessons)
        return AppStrings.string(
            "ics_ready_summary",
            [AppStrings.plural("schedule_lesson_count", count: lessons), ready.dates.resolved]
        )
    }
}

/// The written `.ics` file as the share sheet takes it: `com.apple.ical.ics`, a `public.calendar-event` whose MIME
/// type is `text/calendar`, as Android's `ACTION_SEND`.
struct IcsDocument: Transferable {
    let url: URL

    static let contentType = UTType(filenameExtension: "ics", conformingTo: .calendarEvent) ?? .calendarEvent

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(exportedContentType: contentType) { document in
            SentTransferredFile(document.url)
        }
    }
}

/// `ics_range_custom`: the first and the last day, the last never before the first. The pickers open on [initial],
/// today unless a snapshot fixes it, as Android's date range picker opens on the system's today.
struct IcsDatesPicker: View {
    let pick: (Date, Date) -> Void
    let cancel: () -> Void
    @State private var start: Date
    @State private var end: Date

    init(initial: Date = Date(), pick: @escaping (Date, Date) -> Void, cancel: @escaping () -> Void) {
        self.pick = pick
        self.cancel = cancel
        _start = State(initialValue: initial)
        _end = State(initialValue: initial)
    }

    var body: some View {
        NavigationStack {
            Form {
                DatePicker(selection: $start, displayedComponents: .date) {
                    Text(verbatim: AppStrings.string("ios_ics_dates_start"))
                }
                .accessibilityIdentifier("ics.dates.start")
                DatePicker(selection: $end, in: start..., displayedComponents: .date) {
                    Text(verbatim: AppStrings.string("ios_ics_dates_end"))
                }
                .accessibilityIdentifier("ics.dates.end")
            }
            .environment(\.locale, AppStrings.locale)
            .itmoTint()
            .onChange(of: start) { _, first in
                if end < first { end = first }
            }
            .navigationTitle(Text(verbatim: AppStrings.string("ics_range_custom")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: cancel) {
                        Text(verbatim: AppStrings.string("common_cancel"))
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        pick(start, max(start, end))
                    } label: {
                        Text(verbatim: AppStrings.string("ios_ics_dates_confirm"))
                    }
                    .accessibilityIdentifier("ics.dates.confirm")
                }
            }
        }
        .presentationDetents([.medium])
    }
}

/// A picked day as the ISO day Kotlin parses: the day the picker showed, in the device's calendar.
enum IcsDay {
    static func iso(_ date: Date, calendar: Calendar = .current) -> String {
        let day = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", day.year ?? 0, day.month ?? 0, day.day ?? 0)
    }
}
