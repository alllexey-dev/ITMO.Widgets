import Shared
import SwiftUI

/// One subject (`AppRoutes.RecordbookSubject`, docs/features/subject-page.md) over the shared
/// `RecordbookSubjectViewModel`, pushed on the recordbook stack under the native bar: the name and
/// "kind, semester", the tabs scores, lessons and materials under it (materials first when `materials`, the lesson
/// sheet's links). Links open in the system, the link sheets,
/// the own sheet scores, a teacher, the review editor and the connection settings through the router, the BARS sign-in in
/// `BarsLoginSheet`; a vote that did not reach the server says so.
struct RecordbookSubjectScreen: View {
    let args: RecordbookSubjectArgs
    @Environment(AppRouter.self) private var router
    @Environment(\.openURL) private var openURL
    @State private var model: ObservableViewModel<RecordbookSubjectViewModel, RecordbookSubjectUiState>
    @State private var showsBarsLogin = false
    @State private var message: SettingsMessage?

    init(args: RecordbookSubjectArgs, materials: Bool = false) {
        self.args = args
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            guard let model = store.resolve(type: RecordbookSubjectViewModel.self, parameters: [args])
                as? RecordbookSubjectViewModel else {
                preconditionFailure("Koin resolved no RecordbookSubjectViewModel")
            }
            if materials { model.selectTab(selected: .materials) }
            return model
        })
    }

    var body: some View {
        let content = model.state as? RecordbookSubjectUiStateContent
        RecordbookSubjectContent(state: model.state, actions: actions)
            .refreshable {
                await model.pullRefresh({ $0.refresh(mode: .pull) }, while: {
                    ($0.uiState.value as? RecordbookSubjectUiStateContent)?.refreshing == true
                })
            }
            .itmoDetailHeader(title: content?.title ?? "", subtitle: content?.subtitle.resolved)
            .toolbar {
                if content?.tab == .materials, let links = content?.materials as? SubjectMaterialsContent {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { opened(router.open(AppRoutes.LinkEditor(args: links.args, linkId: nil))) } label: {
                            AppSymbol.add.image
                        }
                        .accessibilityLabel(Text(verbatim: AppStrings.string("subject_link_add")))
                        .accessibilityIdentifier("subject.addLink")
                    }
                }
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
            .sheet(isPresented: $showsBarsLogin) {
                BarsLoginSheet {
                    showsBarsLogin = false
                    model.viewModel.barsSignedIn()
                }
            }
            .observing(model)
            .onEvents(of: model, \.events) { event in
                if let failed = event as? RecordbookSubjectEventVoteFailed {
                    say(AppErrorTextsKt.toUiText(failed.error).resolved)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("recordbook.subject")
    }

    private var actions: RecordbookSubjectContentActions {
        let viewModel = model.viewModel
        return RecordbookSubjectContentActions(
            openLink: { url in URL(string: url).map { openURL($0) } },
            linkActions: { links, id in opened(router.open(AppRoutes.LinkActions(args: links, linkId: id))) },
            allLinks: { opened(router.open(AppRoutes.SubjectLinks(args: $0))) },
            addLink: { opened(router.open(AppRoutes.LinkEditor(args: $0, linkId: nil))) },
            openTeacher: { opened(router.open(AppRoutes.UserProfile(isu: $0))) },
            writeReview: { opened(router.open(AppRoutes.ReviewEditor(args: $0))) },
            connect: { opened(router.open(AppRoutes.Settings(page: "SERVICES"))) },
            sheetScores: { opened(router.open(AppRoutes.SheetScores(args: $0))) },
            barsSignIn: { showsBarsLogin = true },
            openSport: { router.selectedTab = .sport },
            openLesson: { target in
                switch onEnum(of: target) {
                case let .lesson(lesson): opened(router.open(AppRoutes.LessonDetails(args: lesson.args)))
                case let .pendingSport(sport): opened(router.open(AppRoutes.PendingSportDetails(args: sport.args)))
                }
            },
            retry: { viewModel.refresh(mode: .force) },
            selectTab: { viewModel.selectTab(selected: $0) },
            showAllLessons: { viewModel.showAllLessons() },
            confirmBinding: { viewModel.confirmBinding(subjectId: $0) },
            rejectBinding: { viewModel.rejectBinding() },
            retryLessons: { viewModel.retryLessons() },
            vote: { id, up in viewModel.voteLink(id: id, up: up) },
            disconnectSheet: { viewModel.disconnectSheet() }
        )
    }

    private func opened(_ opening: RouteOpening) {
        if opening == .refusedInDemo { say(AppStrings.string("error_demo_unavailable")) }
    }

    private func say(_ text: String) {
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }
}

/// What the subject page asks its host and its ViewModel to do.
struct RecordbookSubjectContentActions {
    var openLink: (String) -> Void = { _ in }
    var linkActions: (SubjectLinksArgs, String) -> Void = { _, _ in }
    var allLinks: (SubjectLinksArgs) -> Void = { _ in }
    var addLink: (SubjectLinksArgs) -> Void = { _ in }
    var openTeacher: (Int32) -> Void = { _ in }
    var writeReview: (TeacherReviewArgs) -> Void = { _ in }
    var connect: () -> Void = {}
    var sheetScores: (SheetScoresArgs) -> Void = { _ in }
    var barsSignIn: () -> Void = {}
    var openSport: () -> Void = {}
    var openLesson: (LessonTarget) -> Void = { _ in }
    var retry: () -> Void = {}
    var selectTab: (SubjectTab) -> Void = { _ in }
    var showAllLessons: () -> Void = {}
    var confirmBinding: (Int64) -> Void = { _ in }
    var rejectBinding: () -> Void = {}
    var retryLessons: () -> Void = {}
    var vote: (String, Bool) -> Void = { _, _ in }
    var disconnectSheet: () -> Void = {}
}

/// The subject page without the ViewModel: loading, the error with retry, or the tabs over a pager of their pages.
struct RecordbookSubjectContent: View {
    let state: RecordbookSubjectUiState
    var actions = RecordbookSubjectContentActions()

    var body: some View {
        Group {
            if let content = state as? RecordbookSubjectUiStateContent {
                SubjectPager(content: content, actions: actions)
            } else {
                ItmoScreenStack {
                    if let failed = state as? RecordbookSubjectUiStateFailed {
                        ItmoErrorView(
                            title: AppStrings.string("common_load_error_title"),
                            description: AppErrorTextsKt.toUiText(failed.error).resolved,
                            retry: actions.retry
                        )
                        .accessibilityIdentifier("subject.error")
                    } else {
                        ItmoLoadingView()
                            .padding(.top, ItmoSpacing.section)
                            .accessibilityIdentifier("subject.loading")
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("subject.list")
    }
}

/// The tabs pinned over a page of each: a tap on a tab slides to its page, a horizontal swipe selects the next tab,
/// and each page keeps its own scroll position.
private struct SubjectPager: View {
    let content: RecordbookSubjectUiStateContent
    let actions: RecordbookSubjectContentActions
    @State private var page: SubjectTab

    private static let tabs = [SubjectTab.scores, SubjectTab.lessons, SubjectTab.materials]

    init(content: RecordbookSubjectUiStateContent, actions: RecordbookSubjectContentActions) {
        self.content = content
        self.actions = actions
        _page = State(initialValue: content.tab)
    }

    var body: some View {
        VStack(spacing: 0) {
            ItmoSegmentedChoice(
                label: AppStrings.string("subject_tabs_label"),
                options: Self.tabs,
                selection: Binding(get: { page }, set: { tab in withAnimation(.snappy) { page = tab } })
            ) { tab in
                switch tab {
                case .scores: AppStrings.string("subject_scores_title")
                case .lessons: AppStrings.string("subject_tab_lessons")
                case .materials: AppStrings.string("subject_tab_materials")
                }
            }
            .accessibilityIdentifier("subject.tabs")
            .padding(.horizontal, ItmoKit.margin)
            TabView(selection: $page) {
                ForEach(Self.tabs, id: \.self) { tab in
                    ScrollView {
                        VStack(alignment: .leading, spacing: ItmoKit.gap) {
                            if let error = content.refreshError {
                                ItmoGroupNote(text: AppStrings.string("recordbook_refresh_error", [AppErrorTextsKt.toUiText(error).resolved]))
                            }
                            switch tab {
                            case .scores: SubjectScoresTab(scores: content.scores, actions: actions)
                            case .lessons: SubjectLessonsTab(lessons: content.lessons, actions: actions)
                            case .materials: SubjectMaterialsTab(materials: content.materials, actions: actions)
                            }
                        }
                        .padding(.horizontal, ItmoKit.margin)
                        .padding(.top, ItmoKit.gap)
                        .padding(.bottom, ItmoSpacing.section)
                    }
                    .tag(tab)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
        }
        .background(Color(.systemGroupedBackground))
        .onChange(of: page) { _, tab in
            if tab != content.tab { actions.selectTab(tab) }
        }
        .onChange(of: content.tab) { _, tab in
            if tab != page { withAnimation(.snappy) { page = tab } }
        }
    }
}
