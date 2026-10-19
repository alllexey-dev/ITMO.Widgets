import Shared
import SwiftUI

/// The link editor (`AppRoutes.LinkEditor`, a new link or an own one) over the shared `LinkEditorViewModel`, a form
/// sheet at full height, since its fields bring the keyboard up: the address, the category (guessed from the site
/// until one is picked), the optional title hinted by the category, then `links_who_sees`. Save waits for an address and a
/// category; a saved link closes the sheet, a failed save says why over it and the form stays. The address starts
/// empty: iOS asks before an app reads the clipboard, so the editor does not read it unasked as Android's does.
struct LinkEditorSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var model: ObservableViewModel<LinkEditorViewModel, LinkEditorUiState>
    @State private var messages = ScheduleMessages()

    init(key: AppRoutes.LinkEditor) {
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters = subjectLinksParameters(args: key.args, linkId: key.linkId)
            guard let model = store.resolve(type: LinkEditorViewModel.self, parameters: parameters)
                as? LinkEditorViewModel else {
                preconditionFailure("Koin resolved no LinkEditorViewModel")
            }
            return model
        })
    }

    var body: some View {
        let state = model.state
        let viewModel = model.viewModel
        NavigationStack {
            LinkEditorForm(
                state: state,
                // The fields read the ViewModel's current value, which an edit changes at once, not the observed one.
                url: Binding(get: { viewModel.uiState.value.url }, set: { viewModel.onUrlChanged(url: $0) }),
                title: Binding(get: { viewModel.uiState.value.title }, set: { viewModel.onTitleChanged(title: $0) }),
                pick: { viewModel.onCategorySelected(category: $0) },
                audience: { viewModel.onAudienceSelected(option: $0) }
            )
            .navigationTitle(Text(verbatim: AppStrings.string(state.editing ? "links_editor_edit" : "links_editor_new")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(role: .cancel) { dismiss() } label: { Text(verbatim: AppStrings.string("common_cancel")) }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button { viewModel.save() } label: {
                        if state.saving { ProgressView() } else { Text(verbatim: AppStrings.string("links_save")) }
                    }
                    .disabled(!state.canSave)
                    .accessibilityLabel(Text(verbatim: AppStrings.string("links_save")))
                    .accessibilityIdentifier("link_editor_save")
                }
            }
        }
        .presentationDetents([.large])
        .interactiveDismissDisabled(state.saving)
        .observing(model)
        .onEvents(of: model, \.events) { event in
            if let failed = event as? LinkEventFailed {
                messages.show(AppErrorTextsKt.toUiText(failed.error).resolved)
            } else {
                dismiss()
            }
        }
        .scheduleMessages(messages)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("resources.linkEditor")
    }
}

/// The editor's form without the ViewModel. The fields take no line breaks, as Android's.
struct LinkEditorForm: View {
    let state: LinkEditorUiState
    @Binding var url: String
    @Binding var title: String
    var pick: (LinkCategory) -> Void = { _ in }
    var audience: (LinkAudienceOption) -> Void = { _ in }

    var body: some View {
        Form {
            Section {
                HStack(alignment: .firstTextBaseline) {
                    TextField(
                        text: singleLine($url),
                        prompt: Text(verbatim: AppStrings.string("links_url_hint")),
                        axis: .vertical
                    ) { Text(verbatim: AppStrings.string("links_url_hint")) }
                    .lineLimit(1...4)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityIdentifier("link_editor_url")
                    if !url.isEmpty {
                        Button { url = "" } label: { AppSymbol.close.image.foregroundStyle(.secondary) }
                            .buttonStyle(.borderless)
                            .accessibilityLabel(Text(verbatim: AppStrings.string("links_url_clear")))
                    }
                }
            } footer: {
                if let error = state.urlError { Text(verbatim: Self.text(of: error)).foregroundStyle(ItmoColor.error) }
            }
            Section {
                ItmoChips(chips: LinkCategory.allCases.map { ItmoChip(label: $0.title().resolved, selected: $0 == state.category) }, wrap: true) {
                    pick(LinkCategory.allCases[$0])
                }
                .padding(.vertical, ItmoSpacing.related)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("link_editor_categories")
            }
            Section {
                let hint = state.category?.title().resolved ?? AppStrings.string("links_name_hint")
                TextField(text: singleLine($title), prompt: Text(verbatim: hint), axis: .vertical) { Text(verbatim: hint) }
                    .lineLimit(1...6)
                    .textInputAutocapitalization(.sentences)
                    .accessibilityIdentifier("link_editor_title")
            } footer: {
                if let error = state.titleError { Text(verbatim: Self.text(of: error)).foregroundStyle(ItmoColor.error) }
            }
            Section {
                let options = state.options
                // `selected` is one of `options` or the `Private` object, so identity finds it.
                let selected = options.firstIndex { ($0 as AnyObject) === (state.selected as AnyObject) } ?? 0
                Picker(selection: Binding(get: { selected }, set: { audience(options[$0]) })) {
                    ForEach(options.indices, id: \.self) { index in
                        let texts = Self.texts(of: options[index], premoderation: state.premoderation)
                        ItmoRowLabel(title: texts.title, subtitle: texts.subtitle).tag(index)
                    }
                } label: {
                    EmptyView()
                }
                .pickerStyle(.inline)
                .labelsHidden()
                .accessibilityIdentifier("link_editor_audiences")
            } header: {
                Text(verbatim: AppStrings.string("links_who_sees"))
            } footer: {
                if state.options.count == 1 {
                    Text(verbatim: AppStrings.string("links_connection_required"))
                        .accessibilityIdentifier("link_editor_connection_hint")
                }
            }
        }
        .itmoTint()
        .disabled(state.saving)
    }

    /// A pasted line break turns into a space.
    private func singleLine(_ text: Binding<String>) -> Binding<String> {
        Binding(get: { text.wrappedValue }, set: { text.wrappedValue = $0.replacing(/\r\n|[\r\n]/, with: " ") })
    }

    private static func text(of error: LinkFieldError) -> String {
        switch error {
        case .urlNotHttps: AppStrings.string("links_invalid_url")
        case .titleTooLong: AppStrings.string("links_title_too_long")
        }
    }

    /// A flow is its schedule name over the kind of classes; a flow and everybody note the review when there is one.
    private static func texts(of option: LinkAudienceOption, premoderation: Bool) -> (title: String, subtitle: String?) {
        if let flow = option as? LinkAudienceOptionFlow {
            let kind = lessonTypeName(typeId: flow.audience.typeId).resolved
            return (flow.audience.label, premoderation ? AppStrings.string("links_visibility_flow_review", [kind]) : kind)
        }
        if option is LinkAudienceOptionAll {
            return (
                AppStrings.string("links_visibility_all"),
                premoderation ? AppStrings.string("links_visibility_all_review") : nil
            )
        }
        return (AppStrings.string("links_visibility_private"), nil)
    }
}
