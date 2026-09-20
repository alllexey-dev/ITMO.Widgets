# ITMO.Widgets social learning roadmap

## Task

Complete the current application refactor and extend ITMO.Widgets into a student assistant with a social layer around lessons, subjects, teachers, friends, schedules, and shared study resources. The Android application must provide user profiles, lesson details, subject pages, moderated teacher reviews, community Google Sheets links, schedule change tracking, BARS mark notifications, range export to a dedicated calendar, map hand-off, and verified App Links.

Keep MyITMO as the source of university data, ITMO.Widgets Backend as the source of social and moderated community data, ITMO.Widgets Core as the typed client contract, and Android as the source of local caches, personal sheet mappings, calendar mappings, and schedule diffs. Achievements, messages, posts, followers, and free-window discovery are outside this roadmap.

## Progress

- Stages 1–4: done before 2026-09-15.
- Stages 5–10: delivered on 2026-09-15 through `vibe/friends-public-profile-plan.md`
  (blocking deferred; FCM implemented locally via `vibe/fcm-foundation-plan.md`,
  with live two-account dev verification pending deployment approval).
- Stages 11–16: delivered on 2026-09-20 through `vibe/lesson-context-plan.md`
  with three corrections: a MyITMO `pair_id` already names one occurrence
  (verified on live data), so no occurrence key or V4 migration was needed and
  the date only guards stale rows; no server-side lesson-details endpoint, the
  app already holds the lesson; friends on a lesson appear only inside the
  details sheet. Schedule changes in the sheet wait for Stage 35.
- Stages 17–18: delivered on 2026-09-20 through `vibe/subject-hub-plan.md`.
  The hub lives in `feature/recordbook` (cross-feature imports are forbidden;
  the schedule side comes through `core/schedule/SubjectLessonsGateway`), the
  study root already had its target shape, and on live data
  `discipline_id == subject_id` for every discipline except PE, so the exact id
  is the primary binding and name confirmation is the fallback. Lesson rows in
  the hub are informational; resources are only the MyITMO `lms_link`.

## Plan Structure

The work is delivered through the completed v2.0.1 baseline and two large product releases:

* v2.0.1 completes Android legacy parity in the current refactor: authentication, onboarding, FCM, QR, widgets, settings, diagnostics, and regression coverage (Stages 1-2).
* v2.1 delivers the social and study-context release. Its internal preparation introduces explicit database migrations, followed by friendship and privacy, own and public profiles, friends on lessons, lesson details, map hand-off, and the subject hub (Stages 3-18).
* v2.2 delivers the community and schedule-intelligence release: moderated resources, teacher reviews, legacy review import, personal Google Sheet mappings, schedule changes, BARS mark notifications, range calendar export, verified App Links, sharing, the QR quick-settings tile and app shortcuts, and the smart home feed (Stages 19-45).

No additional v2.0 feature release is planned after v2.0.1. A v2.0.2 version is reserved only for a required compatibility or bug-fix release discovered after v2.0.1 ships.

Core and Backend retain independent semantic versions. Every Android release documents the minimum compatible Core and Backend versions instead of forcing all three repositories to share the Android version number.

## Execution Plan

### Stage 1: Restore the release-ready v2.0.1 application foundation

**What to add/implement:**

* Restore ITMO.ID login, manual refresh-token login, onboarding, settings, FCM handling, QR screen, QR widget, single-lesson widget, day-schedule widget, widget workers, boot rescheduling, and error diagnostics on top of the current Hilt and repository architecture.
* Implement the v2.1 user-facing settings exactly as specified in `docs/settings.md`: smart widget scheduling and the single widget style are fixed behavior rather than user options.
* Keep authentication tokens local; send only the current ITMO.ID access token to Backend for request authentication.
* Register every restored activity, service, receiver, and widget provider in the manifest.

**Files to edit/create:**

* `app/src/main/AndroidManifest.xml` - register onboarding, login, FCM, widgets, widget collection service, and boot receiver.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/auth/` - add ITMO.ID and manual-token flows.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/onboarding/` - add the first-run flow.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/settings/ui/` - restore account, widget, sport, diagnostics, and service settings.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/qr/ui/` - add the QR screen and ViewModel around the existing QR repository and toolkit.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/widget/` - restore the three widget providers, data managers, and WorkManager workers.
* `app/src/main/java/dev/alllexey/itmowidgets/core/notification/MyFirebaseMessagingService.kt` - receive typed Core FCM payloads and dispatch sport actions.
* `app/src/main/java/dev/alllexey/itmowidgets/core/error/` - restore structured local error logging.
* `docs/settings.md` - authoritative v2.1 settings surface and defaults.

**Framework/Library Documentation:**

* `https://developer.android.com/develop/background-work/background-tasks/persistent` - persistent WorkManager tasks.
* `https://firebase.google.com/docs/cloud-messaging/android/receive` - Android FCM delivery.

**Examples in existing code:**

* `../ITMO.Widgets/app/src/main/java/dev/alllexey/itmowidgets/ui/onboarding/` - legacy onboarding behavior.
* `../ITMO.Widgets/app/src/main/java/dev/alllexey/itmowidgets/ui/widgets/` - legacy widgets.
* `app/src/main/java/dev/alllexey/itmowidgets/data/repository/QrCodeRepositoryImpl.kt` - current repository architecture.

**Verification commands:**

* `./gradlew :app:assembleDebug lintDebug`

### Stage 2: Add regression tests for the restored v2.0.1 foundation

**What to add/implement:**

* Add unit tests for authentication state, onboarding completion, FCM payload dispatch, QR cache invalidation, widget scheduling, widget data selection, and settings persistence.
* Add instrumentation smoke tests for login routing, onboarding routing, and widget configuration activities.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/feature/auth/` - authentication tests.
* `app/src/test/java/dev/alllexey/itmowidgets/feature/widget/` - widget scheduling and data tests.
* `app/src/test/java/dev/alllexey/itmowidgets/core/notification/` - FCM dispatch tests.
* `app/src/androidTest/java/dev/alllexey/itmowidgets/` - navigation smoke tests.

**Examples in existing code:**

* `app/src/test/java/dev/alllexey/itmowidgets/core/time/DefaultAcademicTimeProviderTest.kt` - local unit-test style.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

### Stage 3: Introduce explicit Backend database migrations

**What to add/implement:**

* Use PostgreSQL 17 and Flyway with a fresh V1 schema containing all currently implemented Backend entities. Do not import legacy MariaDB data; production cutover waits for the Android v2.1 release and separate approval.
* Use `spring.jpa.hibernate.ddl-auto=validate` in all environments, including local development. Disable automatic baseline and clean; fail on migration checksum or schema mismatches.
* Declare named indexes, foreign keys, UUID types, timestamp-with-time-zone columns and identity keys for the current schema. Port native upsert and delete queries to PostgreSQL and use explicit non-reserved lesson-time column names.

**Files to edit/create:**

* `../itmo-widgets-backend/build.gradle.kts` - use the PostgreSQL JDBC driver, Flyway PostgreSQL module and real-PostgreSQL Testcontainers tests.
* `../itmo-widgets-backend/src/main/resources/application.properties` - configure Flyway and Hibernate validation.
* `../itmo-widgets-backend/src/main/resources/db/migration/V1__initial_postgresql_schema.sql` - declare the fresh schema.
* `../itmo-widgets-backend/deploy/` - PostgreSQL local/server deployment templates and non-superuser role initializer.
* `../itmo-widgets-backend/docs/ops/database.md` - gated fresh-start cutover, backup, bootstrap and coordinated rollback instructions.

**Framework/Library Documentation:**

* `https://documentation.red-gate.com/flyway/reference/usage/community-plugins-and-integrations/community-plugins-and-integrations-spring-boot` - Spring Boot Flyway integration.

**Examples in existing code:**

* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/User.kt` - current JPA mapping.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/LessonEntity.kt` - current indexes and constraints.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build`

### Stage 4: Add migration integration tests

**What to add/implement:**

* Run repository and migration integration tests on disposable PostgreSQL 17 containers. Apply production Flyway migrations and require Hibernate validation; never substitute H2-generated DDL.
* Verify empty-schema creation, non-destructive restart, unknown-schema refusal, checksum mismatch, disabled clean, schema drift rejection and non-superuser migration. Exercise UUID/time/text/enum relationships and every native SQL mutation. Use synthetic data only; legacy MariaDB data is intentionally not imported.

**Files to edit/create:**

* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/repositories/PostgreSqlRepositoryTest.kt` - isolated datasource with production Flyway and validation settings.
* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/repositories/PostgreSqlMigrationTest.kt` - migration safety and persistence tests.
* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/repositories/NativeRepositoryMutationTest.kt` - PostgreSQL native query regressions.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

### Stage 5: Implement Backend social identities, friendship states, blocking, and privacy

**What to add/implement:**

* Add stable public user IDs and explicit `NONE`, `OUTGOING`, `INCOMING`, `FRIENDS`, and `BLOCKED` relationship semantics.
* Replace reciprocal active requests with one friendship record and explicit send, accept, reject, cancel, remove, block, and unblock operations.
* Keep name, group, and ISU visible; do not expose ineffective privacy controls for identity fields already available through official university services.
* Add separate friend-only schedule and sport sharing settings, enabled by default, plus friend-request visibility. Enforce reciprocal access independently for schedule and sport: a user who disables sharing cannot view the corresponding data of friends.
* Add public-review visibility to the server contract for v2.2 without exposing a dead Android setting in v2.1.
* Return viewer-scoped capabilities instead of another user's raw settings.
* Add user search by exact ISU and normalized name or group with bounded results and rate limiting.

**Files to edit/create:**

* `../itmo-widgets-backend/src/main/resources/db/migration/V2__social_profiles.sql` - migrate relationships and add privacy columns.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/FriendshipEntity.kt` - explicit friendship lifecycle.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/UserBlockEntity.kt` - blocking relation.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/UserSettingsEntity.kt` - privacy settings.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/services/ProfileAccessService.kt` - central capability calculation.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/services/FriendService.kt` - explicit state transitions.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/controllers/FriendController.kt` - social actions.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/controllers/UserController.kt` - own profile, public profile, and search endpoints.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/configs/SecurityConfig.kt` - endpoint authorization and deny-by-default rules.

**Framework/Library Documentation:**

* `https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html` - request authorization.
* `https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html` - service-level authorization.

**Examples in existing code:**

* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/services/FriendService.kt` - current reciprocal-request behavior.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/configs/GlobalExceptionHandler.kt` - API error mapping.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build`

### Stage 6: Add Backend social authorization tests

**What to add/implement:**

* Test every friendship transition, duplicate request, self-request, block, unblock, and removal case.
* Test fixed identity visibility and capability calculation for strangers, incoming requests, outgoing requests, friends, and blocked users.
* Test schedule and sport sharing independently, including default-enabled state and reciprocal denial when the viewer disabled the corresponding sharing control.
* Test search authorization, result limits, and blocked-user exclusion.

**Files to edit/create:**

* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/services/FriendServiceTest.kt` - state-machine tests.
* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/services/ProfileAccessServiceTest.kt` - privacy matrix tests.
* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/controllers/UserControllerTest.kt` - endpoint tests.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

### Stage 7: Extend Core with social profile contracts

**What to add/implement:**

* Add typed models for own profile, viewer-scoped public profile, relationship state, profile capabilities, separate schedule and sport sharing settings, friend requests, and paged user search.
* Replace ambiguous friend endpoints with explicit state-transition endpoints.
* Keep public author identity nullable so anonymous content cannot expose author fields through serialization.

**Files to edit/create:**

* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/model/social/ProfileModels.kt` - profile and capability DTOs.
* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/model/social/FriendshipModels.kt` - relationship DTOs and requests.
* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/ItmoWidgetsApi.kt` - social endpoints.

**Examples in existing code:**

* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/model/Responses.kt` - current DTO conventions.
* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/ItmoWidgetsApi.kt` - Retrofit interface conventions.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build`

### Stage 8: Add Core social contract tests

**What to add/implement:**

* Add JSON round-trip tests for every relationship state, profile capability combination, and anonymous author representation.
* Add MockWebServer contract tests for all social endpoints.

**Files to edit/create:**

* `../itmo-widgets-core/src/test/kotlin/social/ProfileModelsTest.kt` - serialization tests.
* `../itmo-widgets-core/src/test/kotlin/social/SocialApiTest.kt` - Retrofit contract tests.
* `../itmo-widgets-core/build.gradle.kts` - add MockWebServer test dependency.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test`

### Stage 9: Add Android own-profile, public-profile, and friend-management flows

**What to add/implement:**

* Replace the debug-only profile root with identity, friends, requests, privacy, personal content, integrations, settings, and debug sections.
* Add a full-screen public user profile with context-aware friendship actions and schedule or sport entry points based on Backend capabilities.
* Add friend list, incoming and outgoing requests, blocked users, and bounded add-friend search.
* Open public profiles from friend selection and preserve each bottom-navigation back stack.

**Files to edit/create:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/me/ui/MeFragment.kt` - own-profile root.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/social/profile/UserProfileFragment.kt` - public profile UI.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/social/profile/UserProfileViewModel.kt` - profile state and actions.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/social/friends/FriendsFragment.kt` - friends and requests.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/social/search/UserSearchFragment.kt` - add-friend search.
* `app/src/main/java/dev/alllexey/itmowidgets/domain/repository/SocialRepository.kt` - Android social abstraction.
* `app/src/main/java/dev/alllexey/itmowidgets/data/repository/SocialRepositoryImpl.kt` - Core-backed repository.
* `app/src/main/res/navigation/main_nav_graph.xml` - profile and social destinations.
* `app/src/main/res/layout/fragment_me.xml` - own-profile layout.
* `app/src/main/res/layout/fragment_user_profile.xml` - public-profile layout.

**Examples in existing code:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/friendselector/FriendSelectorViewModel.kt` - current social state handling.
* `app/src/main/java/dev/alllexey/itmowidgets/core/ui/AvatarView.kt` - avatar rendering.

**Verification commands:**

* `./gradlew :app:assembleDebug lintDebug`

### Stage 10: Add Android social flow tests

**What to add/implement:**

* Test profile rendering for every relationship and capability state.
* Test friend-request actions, blocked-user behavior, independent schedule and sport sharing updates, reciprocal access, defaults, and restoration after process recreation.
* Add navigation tests from friend selector, request list, and non-anonymous author to a public profile.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/feature/social/profile/UserProfileViewModelTest.kt` - profile state tests.
* `app/src/test/java/dev/alllexey/itmowidgets/feature/social/friends/FriendsViewModelTest.kt` - friend action tests.
* `app/src/androidTest/java/dev/alllexey/itmowidgets/feature/social/SocialNavigationTest.kt` - navigation tests.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

### Stage 11: Implement Backend lesson context and privacy-safe friends-on-lesson

**What to add/implement:**

* Introduce a stable lesson-occurrence key from pair ID and date and use it for detail and sharing routes.
* Return lesson details plus only mutual, non-blocked friends who enabled schedule sharing.
* Reject access to another user's schedule when friendship or schedule-sharing authorization is missing.
* Remove the current unrestricted `usersByPairId` behavior.

**Files to edit/create:**

* `../itmo-widgets-backend/src/main/resources/db/migration/V4__lesson_occurrences.sql` - add occurrence keys and indexes.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/LessonEntity.kt` - occurrence identity.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/repositories/LessonRepository.kt` - authorized friend queries.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/services/LessonContextService.kt` - detail and capability logic.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/controllers/ScheduleController.kt` - detail and friend endpoints.

**Examples in existing code:**

* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/controllers/ScheduleController.kt` - existing schedule endpoints and missing friendship checks.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build`

### Stage 12: Add Backend lesson-context tests

**What to add/implement:**

* Test occurrence identity, date boundaries, duplicate pair IDs, schedule visibility, blocked users, stale synchronized schedules, and friend filtering.

**Files to edit/create:**

* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/services/LessonContextServiceTest.kt` - context tests.
* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/controllers/ScheduleControllerTest.kt` - authorization tests.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

### Stage 13: Extend Core with lesson-context contracts

**What to add/implement:**

* Add lesson-occurrence, friend-on-lesson, and detail DTOs.
* Correct the current `{pairId}` Retrofit parameter mismatch and add the occurrence date parameter.

**Files to edit/create:**

* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/model/schedule/LessonContextModels.kt` - occurrence and detail DTOs.
* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/ItmoWidgetsApi.kt` - lesson-context endpoints.

**Examples in existing code:**

* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/model/Requests.kt` - lesson DTO field conventions.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build`

### Stage 14: Add Core lesson-context tests

**What to add/implement:**

* Test occurrence serialization, endpoint path construction, date query encoding, and friend-list privacy fields.

**Files to edit/create:**

* `../itmo-widgets-core/src/test/kotlin/schedule/LessonContextApiTest.kt` - contract tests.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test`

### Stage 15: Add Android lesson details, friend profiles, and map hand-off

**What to add/implement:**

* Open a lesson-details bottom sheet from every ordinary schedule card.
* Show lesson metadata, schedule changes, teacher, subject, and privacy-filtered friend avatars.
* Open a friend's full profile from an avatar.
* Add a curated local ITMO building directory and always launch a generic system `geo:` intent. Do not add a preferred-map setting or provider-specific routing.

**Files to edit/create:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/ui/details/LessonDetailsBottomSheet.kt` - details UI.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/presentation/details/LessonDetailsViewModel.kt` - detail state.
* `app/src/main/java/dev/alllexey/itmowidgets/core/ui/navigation/MapLauncher.kt` - generic system geo intent and safe missing-handler fallback.
* `app/src/main/java/dev/alllexey/itmowidgets/core/location/BuildingDirectory.kt` - building lookup.
* `app/src/main/res/raw/itmo_buildings.json` - building IDs, aliases, addresses, and coordinates.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/ui/LessonAdapter.kt` - details click action.
* `app/src/main/res/layout/fragment_lesson_details.xml` - details layout.

**Framework/Library Documentation:**

* `https://developer.android.com/training/basics/intents/sending` - safe implicit map intents.

**Examples in existing code:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/sport/ui/common/SportCommonDetailsBottomSheet.kt` - current detail-sheet pattern.

**Verification commands:**

* `./gradlew :app:assembleDebug lintDebug`

### Stage 16: Add Android lesson-detail and map tests

**What to add/implement:**

* Test building alias resolution, generic geo URI generation, missing-handler fallback, friend capability rendering, and lesson-detail navigation.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/core/location/BuildingDirectoryTest.kt` - building tests.
* `app/src/test/java/dev/alllexey/itmowidgets/core/ui/navigation/MapLauncherTest.kt` - URI tests.
* `app/src/test/java/dev/alllexey/itmowidgets/feature/schedule/presentation/details/LessonDetailsViewModelTest.kt` - state tests.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest`

### Stage 17: Implement the Android subject hub

**What to add/implement:**

* Replace the recordbook-only subject detail with a subject hub containing overview, controls, upcoming lessons, teachers, and resources.
* Keep the study root simple: period selection, summary, an optional attention section, and the remaining subject list without search or manual filters.
* Add a subject-context resolver that combines recordbook discipline IDs and schedule subject or flow IDs, requests user confirmation for ambiguous normalized-name matches, and persists confirmed bindings locally.

**Files to edit/create:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/domain/model/SubjectModels.kt` - subject context and overview.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/domain/SubjectRepository.kt` - aggregate contract.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/data/SubjectRepositoryImpl.kt` - recordbook and schedule aggregation.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/data/SubjectBindingStore.kt` - confirmed local bindings.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/ui/SubjectFragment.kt` - subject hub.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/presentation/SubjectViewModel.kt` - overview, controls, lessons, teachers, and resources state.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/ui/RecordbookFragment.kt` - simplify the root list and route to the subject hub.
* `app/src/main/res/navigation/overlay_nav_graph.xml` - canonical subject destination.

**Examples in existing code:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/ui/RecordbookSubjectFragment.kt` - existing control details.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/data/RecordbookRepositoryImpl.kt` - recordbook source.

**Verification commands:**

* `./gradlew :app:assembleDebug lintDebug`

### Stage 18: Add subject-hub tests

**What to add/implement:**

* Test exact IDs, normalized-name proposals, ambiguous matches, confirmed bindings, period changes, and aggregation of recordbook and schedule data.
* Add UI-state tests for subjects with no controls, no schedule match, multiple teachers, and no resources.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/feature/subject/data/SubjectRepositoryImplTest.kt` - aggregation tests.
* `app/src/test/java/dev/alllexey/itmowidgets/feature/subject/data/SubjectBindingStoreTest.kt` - binding tests.
* `app/src/test/java/dev/alllexey/itmowidgets/feature/subject/presentation/SubjectViewModelTest.kt` - state tests.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest`

### Stage 19: Implement Backend community resources and reusable moderation

**What to add/implement:**

* Add subject and flow-scoped resources with normalized URLs, type, title, academic period, submitter, moderation status, confirmations, reports, and last-verified timestamp.
* Permit private selections immediately, expose community resources only after moderation, and prevent duplicate normalized Google Sheet links.
* Add moderator roles, auditable moderation decisions, and protected moderation endpoints reusable by teacher reviews.
* Restrict accepted resource schemes and hosts; never fetch arbitrary submitted URLs.

**Files to edit/create:**

* `../itmo-widgets-backend/src/main/resources/db/migration/V5__community_resources.sql` - resources, confirmations, reports, roles, and moderation cases.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/SubjectResourceEntity.kt` - community resource.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/ModerationCaseEntity.kt` - moderation lifecycle.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/UserRoleEntity.kt` - moderator authority.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/services/SubjectResourceService.kt` - resource rules.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/services/ModerationService.kt` - generic moderation actions.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/controllers/SubjectResourceController.kt` - user endpoints.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/controllers/ModerationController.kt` - moderator endpoints.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/configs/SecurityConfig.kt` - moderator authority rules.

**Framework/Library Documentation:**

* `https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html` - moderator method authorization.

**Examples in existing code:**

* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/SportAutoSignEntity.kt` - timestamped lifecycle entity.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build`

### Stage 20: Add Backend resource and moderation tests

**What to add/implement:**

* Test URL normalization, duplicates, academic scoping, private selection, approval, rejection, reports, confirmations, moderator authorization, and audit-history preservation.
* Test rejection of unsupported schemes and hosts.

**Files to edit/create:**

* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/services/SubjectResourceServiceTest.kt` - resource rules.
* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/services/ModerationServiceTest.kt` - lifecycle tests.
* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/controllers/ModerationControllerTest.kt` - authorization tests.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

### Stage 21: Extend Core with resource and moderation-status contracts

**What to add/implement:**

* Add subject scope, resource type, resource status, confirmation, report, and personal selection DTOs.
* Add user resource endpoints; keep moderator-only contracts in a separate interface not exposed through the default Android client.

**Files to edit/create:**

* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/model/resources/SubjectResourceModels.kt` - resource DTOs.
* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/ItmoWidgetsApi.kt` - user resource endpoints.
* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/ItmoWidgetsModerationApi.kt` - moderator contract.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build`

### Stage 22: Add Core resource contract tests

**What to add/implement:**

* Test DTO round trips, subject-scope parameters, nullable private selections, and moderator contract separation.

**Files to edit/create:**

* `../itmo-widgets-core/src/test/kotlin/resources/SubjectResourceModelsTest.kt` - serialization tests.
* `../itmo-widgets-core/src/test/kotlin/resources/SubjectResourceApiTest.kt` - endpoint tests.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test`

### Stage 23: Add Android subject-resource UI

**What to add/implement:**

* Show the selected resource and approved community alternatives inside the subject hub.
* Add flows to select, submit, confirm, and report a resource and show pending or rejected states for the submitter.
* Add `My links` under the own profile.

**Files to edit/create:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/domain/SubjectResourceRepository.kt` - resource contract.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/data/SubjectResourceRepositoryImpl.kt` - Core-backed implementation.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/ui/resources/SubjectResourcesFragment.kt` - resource list.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/presentation/resources/SubjectResourcesViewModel.kt` - selection and moderation state.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/ui/resources/MyResourcesFragment.kt` - submitted links, opened from the profile tab through `AppNavigator`.

**Examples in existing code:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/sport/ui/sign/MultiSelectSearchableAdapter.kt` - selectable-list conventions.

**Verification commands:**

* `./gradlew :app:assembleDebug lintDebug`

### Stage 24: Add Android resource tests

**What to add/implement:**

* Test community ordering, private selection, pending and rejected submissions, confirmation toggles, reporting, and offline display of the last selected link.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/feature/subject/presentation/resources/SubjectResourcesViewModelTest.kt` - resource state tests.
* `app/src/androidTest/java/dev/alllexey/itmowidgets/feature/subject/SubjectResourcesFlowTest.kt` - submission flow.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

### Stage 25: Implement Backend teacher reviews and review moderation

**What to add/implement:**

* Add teachers keyed by teacher ISU, review revisions, category ratings, optional subject and period scope, anonymous-by-default publication, reports, and moderation history.
* Permit reviews only from users whose synchronized schedule contains the teacher and subject context.
* Enforce one active review per user, teacher, subject, and period; edits create a new pending revision.
* Return no author field for anonymous reviews and a viewer-scoped public profile summary only for approved non-anonymous reviews.

**Files to edit/create:**

* `../itmo-widgets-backend/src/main/resources/db/migration/V6__teacher_reviews.sql` - teacher and review tables.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/TeacherEntity.kt` - canonical teacher.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/TeacherReviewEntity.kt` - review identity and lifecycle.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/model/TeacherReviewRevisionEntity.kt` - immutable revision data.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/services/TeacherReviewService.kt` - eligibility and publication rules.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/controllers/TeacherReviewController.kt` - teacher and review endpoints.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/controllers/ModerationController.kt` - review moderation actions.

**Examples in existing code:**

* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/services/LessonService.kt` - synchronized lesson access.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/configs/GlobalExceptionHandler.kt` - business-rule errors.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build`

### Stage 26: Add Backend teacher-review tests

**What to add/implement:**

* Test eligibility, duplicate prevention, anonymous serialization, non-anonymous author capabilities, revision moderation, reports, aggregate thresholds, and blocked-user behavior.

**Files to edit/create:**

* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/services/TeacherReviewServiceTest.kt` - review rules.
* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/controllers/TeacherReviewControllerTest.kt` - response privacy tests.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

### Stage 27: Extend Core with teacher-review contracts

**What to add/implement:**

* Add teacher profile, rating category, aggregate, review revision, moderation state, report, and create or edit request DTOs.
* Default `anonymous` to `true` in create requests.

**Files to edit/create:**

* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/model/reviews/TeacherReviewModels.kt` - review DTOs.
* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/ItmoWidgetsApi.kt` - user review endpoints.
* `../itmo-widgets-core/src/main/kotlin/dev/alllexey/itmowidgets/core/ItmoWidgetsModerationApi.kt` - moderation endpoints.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build`

### Stage 28: Add Core teacher-review tests

**What to add/implement:**

* Verify that anonymous review JSON contains no author object, non-anonymous review JSON supports a public profile summary, and create requests default to anonymous publication.

**Files to edit/create:**

* `../itmo-widgets-core/src/test/kotlin/reviews/TeacherReviewModelsTest.kt` - privacy serialization tests.
* `../itmo-widgets-core/src/test/kotlin/reviews/TeacherReviewApiTest.kt` - endpoint tests.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew test`

### Stage 29: Add Android teacher profiles and review flows

**What to add/implement:**

* Add teacher profiles reachable from lessons and subjects, rating summaries, subject filters, approved review lists, and report actions.
* Add create and edit forms with anonymity enabled by default and explicit confirmation before non-anonymous publication.
* Add `My reviews` with pending, approved, rejected, and superseded revisions under the own profile.
* Open a public user profile only from approved non-anonymous reviews.

**Files to edit/create:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/teacher/ui/TeacherProfileFragment.kt` - teacher profile.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/teacher/presentation/TeacherProfileViewModel.kt` - teacher and review state.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/teacher/ui/EditTeacherReviewFragment.kt` - create and edit form.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/teacher/ui/MyReviewsFragment.kt` - own moderation states, opened from the profile tab through `AppNavigator`.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/teacher/domain/TeacherReviewRepository.kt` - Android contract.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/teacher/data/TeacherReviewRepositoryImpl.kt` - Core-backed implementation.
* `app/src/main/res/navigation/overlay_nav_graph.xml` - teacher and review destinations.

**Examples in existing code:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/presentation/RecordbookSubjectViewModel.kt` - detail-state pattern.

**Verification commands:**

* `./gradlew :app:assembleDebug lintDebug`

### Stage 30: Add Android teacher-review tests

**What to add/implement:**

* Test anonymous default state, explicit named-review confirmation, moderation-state rendering, author navigation, eligibility errors, and process restoration of unsent review text.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/feature/teacher/presentation/TeacherProfileViewModelTest.kt` - teacher state tests.
* `app/src/test/java/dev/alllexey/itmowidgets/feature/teacher/presentation/EditTeacherReviewViewModelTest.kt` - form tests.
* `app/src/androidTest/java/dev/alllexey/itmowidgets/feature/teacher/TeacherReviewFlowTest.kt` - end-to-end UI flow.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

### Stage 31: Add an auditable legacy-review importer

**What to add/implement:**

* Add a separate CLI entry point that reads configured Google Sheets or exported CSV files, normalizes rows, stores source-row provenance, deduplicates reviews, matches teachers by ISU first, and emits unresolved names for manual mapping.
* Import every row into a non-public legacy batch and require moderation before publication.
* Label published imported reviews as legacy and exclude them from verified-user achievements or counts.

**Files to edit/create:**

* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/importer/LegacyReviewImportCommand.kt` - CLI entry point.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/importer/LegacyReviewParser.kt` - source-specific parsing.
* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/importer/TeacherMatcher.kt` - deterministic matching and unresolved output.
* `../itmo-widgets-backend/src/main/resources/import/legacy-review-sources.yaml` - source mappings without credentials.

**Examples in existing code:**

* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/services/SportUpdateService.kt` - batch ingestion and mapping pattern.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build`

### Stage 32: Add importer fixture and dry-run tests

**What to add/implement:**

* Add anonymized fixtures for every source format and golden dry-run reports.
* Test duplicate rows, missing teacher ISU, conflicting names, blank reviews, malformed ratings, repeated imports, and transaction rollback.

**Files to edit/create:**

* `../itmo-widgets-backend/src/test/resources/import/` - anonymized source fixtures.
* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/importer/LegacyReviewImporterTest.kt` - importer tests.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test`

### Stage 33: Add personal public-Google-Sheet score mappings

**What to add/implement:**

* Support only publicly readable Google Sheets links selected from approved subject resources.
* Parse sheet ID and gid, let the user configure header row, student lookup column, own row or identifier, score columns, and total calculation.
* Download and parse values on device, store mappings and extracted personal results locally, and never upload the complete grade table to Backend.
* Treat unsupported or changed formats as `BROKEN_SCHEMA` and keep the resource link usable without score parsing.

**Files to edit/create:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/data/sheets/PublicGoogleSheetClient.kt` - public sheet download.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/domain/sheets/GoogleSheetUrlParser.kt` - URL normalization.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/domain/sheets/SheetScoreExtractor.kt` - configured extraction.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/data/sheets/SheetMappingStore.kt` - local mappings and last result.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/ui/sheets/SheetMappingFragment.kt` - mapping UI.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/subject/presentation/sheets/SheetScoreViewModel.kt` - refresh and schema state.

**Framework/Library Documentation:**

* `https://developers.google.com/workspace/sheets/api/guides/values` - Google Sheets value model.

**Examples in existing code:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/data/local/ScheduleLocalDataSourceImpl.kt` - local cached data pattern.

**Verification commands:**

* `./gradlew :app:assembleDebug lintDebug`

### Stage 34: Add Google Sheet parser tests

**What to add/implement:**

* Test URL forms, gid parsing, Cyrillic names, ISU lookup, merged or blank cells represented in exported values, decimal formats, formulas rendered as values, missing columns, changed headers, and large sheets.

**Files to edit/create:**

* `app/src/test/resources/sheets/` - anonymized table fixtures.
* `app/src/test/java/dev/alllexey/itmowidgets/feature/subject/domain/sheets/GoogleSheetUrlParserTest.kt` - URL tests.
* `app/src/test/java/dev/alllexey/itmowidgets/feature/subject/domain/sheets/SheetScoreExtractorTest.kt` - extraction tests.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest`

### Stage 35: Implement local schedule-change detection and notifications

**What to add/implement:**

* Persist normalized schedule snapshots and compute added, removed, moved, teacher-changed, format-changed, and room-changed events.
* Show unread changes on the home feed, affected day, affected lesson, and a change-history screen.
* Run a unique periodic WorkManager synchronization with network constraints and local notifications; do not claim immediate delivery because Android controls periodic execution.

**Files to edit/create:**

* `app/build.gradle.kts` - add Room runtime and compiler dependencies.
* `app/src/main/java/dev/alllexey/itmowidgets/app/AppDatabase.kt` - Room database composed in the app layer from feature-owned entities and DAOs.
* `app/src/main/java/dev/alllexey/itmowidgets/di/StorageModule.kt` - provide the database and the DAOs.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/data/changes/ScheduleSnapshotEntity.kt` - normalized snapshots.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/data/changes/ScheduleChangeEntity.kt` - durable diffs.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/domain/changes/ScheduleDiffEngine.kt` - pure diff logic.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/work/ScheduleSyncWorker.kt` - unique periodic sync.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/ui/changes/ScheduleChangesFragment.kt` - history UI.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/data/repository/ScheduleRepositoryImpl.kt` - snapshot and diff persistence.

**Framework/Library Documentation:**

* `https://developer.android.com/develop/background-work/background-tasks/persistent` - persistent background work.
* `https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work` - unique WorkManager jobs.

**Examples in existing code:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/data/local/ScheduleLocalDataSourceImpl.kt` - current schedule cache.

**Verification commands:**

* `./gradlew :app:assembleDebug lintDebug`

### Stage 36: Add schedule-diff and worker tests

**What to add/implement:**

* Test reorder-only responses, added and removed lessons, date or time moves, room and teacher changes, duplicate refreshes, stale snapshots, and unread-state transitions.
* Test unique worker scheduling, retry, offline behavior, and notification suppression for unchanged data.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/feature/schedule/domain/changes/ScheduleDiffEngineTest.kt` - diff cases.
* `app/src/androidTest/java/dev/alllexey/itmowidgets/feature/schedule/work/ScheduleSyncWorkerTest.kt` - WorkManager tests.
* `app/src/androidTest/java/dev/alllexey/itmowidgets/app/AppDatabaseMigrationTest.kt` - Room migrations.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

### Stage 37: Implement BARS mark tracking and notifications

**What to add/implement:**

* Track the BARS journal in the background and notify about new marks, changed marks, and changed approvals. Everything stays on the device: BARS data never reaches Backend, and the feature does not depend on the custom-services opt-in.
* Renew the BARS token in the background without a WebView: `BarsCookieSilentLogin` replays the official OIDC authorization URL through OkHttp with the ITMO.ID cookies read from `CookieManager`, accepts only the exact callback with a checked `state`, and exchanges the code through the library's `BarsCodeSupplier`. No credentials, no JavaScript, no cookie leaves the device. Foreground renewal keeps `BarsWebSilentLogin`. Record the rule as decision 0008.
* Persist a normalized per-checkpoint snapshot (discipline, checkpoint plan, checkpoint, mark, approval) per ISU in the Room database introduced in Stage 35. `BarsMarkDiffEngine` is pure: it emits `MarkAdded`, `MarkChanged`, and `ApprovalChanged` events, ignores reorder-only responses, treats an empty journal (`total = 0`, no marks) as "no marks" rather than a removal, and skips plans with `has_course_project` exactly like the overlay mapper. The first successful sync after enabling only writes the baseline and notifies nothing.
* Run a unique periodic WorkManager job (`bars-mark-sync`, every three hours, network constraint, `@HiltWorker`). It exits quietly without a BARS session, backs off after a failed renewal, and when the ITMO.ID cookie session is gone posts one `Войдите в БАРС` notification and stays silent until the next successful BARS login. Opening the recordbook with the `БАРС` chip runs the same diff on the fresh journal so foreground use advances the baseline. Do not claim immediate delivery because Android controls periodic execution.
* Add the `BARS` notification channel next to `SPORT` and `FRIENDS`. One notification per subject per sync with a stable id: subject and checkpoint in the title, the mark in the expanded text, `VISIBILITY_PRIVATE` with the public version `Новая оценка в БАРС`. Tapping opens the recordbook subject with the chip on through `MainActivityIntentRouting`.
* Add the `Следить за оценками БАРС` toggle to the recordbook BARS section of settings. It appears once a BARS session exists, is enabled on the first successful BARS login, and disabling cancels the job and deletes the snapshot. `BarsMarkTrackingRepositoryImpl` is a `SessionDataCleaner`, so sign-out and account change clear snapshots, events, and the job.

**Files to edit/create:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/domain/BarsMarkDiffEngine.kt` - pure snapshot diff producing `BarsMarkEvent` values.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/domain/BarsMarkTrackingRepository.kt` - contract: enable and disable, snapshot sync, unread events.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/data/bars/BarsMarkSnapshotEntity.kt` - Room entity and DAO registered in the Stage 35 database.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/data/bars/BarsMarkEventEntity.kt` - durable events with read state.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/data/bars/BarsMarkTrackingRepositoryImpl.kt` - journal fetch, diff, persistence, session cleaner.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/data/bars/BarsCookieSilentLogin.kt` - OkHttp cookie-replay renewal for background use.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/work/BarsMarkSyncWorker.kt` - unique periodic job and its scheduling entry point.
* `app/src/main/java/dev/alllexey/itmowidgets/core/notification/AppNotificationChannels.kt` - `BARS` channel.
* `app/src/main/java/dev/alllexey/itmowidgets/app/MainActivityIntentRouting.kt` - route to the recordbook subject with the chip on.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/settings/presentation/SettingsViewModel.kt` - the tracking toggle.
* `app/src/main/java/dev/alllexey/itmowidgets/di/RecordbookModule.kt` - bindings and the worker entry point.
* `docs/decisions/0008-bars-background-renewal.md` - cookie replay through OkHttp is the only background renewal path.
* `docs/features/recordbook.md`, `docs/features/notifications.md`, `docs/settings.md` - current-state documentation of tracking, the channel, and the toggle.

**Framework/Library Documentation:**

* `https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work` - unique periodic WorkManager jobs.
* `https://developer.android.com/develop/ui/views/notifications/build-notification#lockscreenNotification` - lock-screen visibility.
* `https://developer.android.com/reference/android/webkit/CookieManager` - reading WebView cookies outside a WebView.
* `../MyItmoApi/README.md` - `api.bars.Bars`, the auth helper, and `BarsCodeSupplier`.

**Examples in existing code:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/data/bars/BarsSilentLogin.kt` - current WebView renewal and the `BarsSilentLogin` contract.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/recordbook/domain/RecordbookBarsMerge.kt` - pure domain logic over the BARS journal.
* `app/src/main/java/dev/alllexey/itmowidgets/core/notification/FcmWork.kt` - unique WorkManager chain with a network constraint.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/work/ScheduleWidgetWork.kt` - worker scheduling entry point.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

### Stage 38: Add BARS mark tracking tests

**What to add/implement:**

* Test the diff engine: baseline run, added mark, changed mark, approval change, reorder-only response, empty journal, course-project plan, and a removed checkpoint that produces no notification.
* Test the repository: per-ISU snapshots, enable and disable, unread transitions, and the session cleaner.
* Test cookie renewal with `MockWebServer`: callback with a matching `state`, mismatched `state` rejected, missing cookies reported as a session-required error, one retry on 401.
* Test the worker: unique scheduling, quiet exit without a session, a single `Войдите в БАРС` notification after a dead cookie session, no notification for unchanged data, no duplicate notification for one event.
* Test notification rendering and routing: private visibility with the public fallback, stable ids, and the subject route with the chip on.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/feature/recordbook/domain/BarsMarkDiffEngineTest.kt` - diff cases.
* `app/src/test/java/dev/alllexey/itmowidgets/feature/recordbook/data/bars/BarsMarkTrackingRepositoryImplTest.kt` - persistence and cleaner cases with a fake journal source.
* `app/src/test/java/dev/alllexey/itmowidgets/feature/recordbook/data/bars/BarsCookieSilentLoginTest.kt` - `MockWebServer` renewal cases.
* `app/src/test/java/dev/alllexey/itmowidgets/app/MainActivityIntentRoutingTest.kt` - BARS routing cases.
* `app/src/androidTest/java/dev/alllexey/itmowidgets/feature/recordbook/work/BarsMarkSyncWorkerTest.kt` - WorkManager cases.

**Examples in existing code:**

* `app/src/test/java/dev/alllexey/itmowidgets/feature/recordbook/RecordbookFakes.kt` - fakes for the recordbook feature.
* `app/src/test/java/dev/alllexey/itmowidgets/core/notification/FcmPayloadDispatcherTest.kt` - notification handler tests.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

### Stage 39: Add range-based schedule export and optional calendar synchronization

**What to add/implement:**

* Add a schedule-root action that exports next week, next two weeks, current semester, or a custom range.
* Create or select a dedicated calendar, store stable occurrence-to-event mappings, update existing events, and remove cancelled events without duplicating entries.
* Keep per-lesson and per-subject calendar actions absent.
* Add optional synchronization of an exported range after schedule changes.

**Files to edit/create:**

* `app/src/main/AndroidManifest.xml` - request calendar permissions only for direct provider synchronization.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/data/export/CalendarExporter.kt` - range export and updates.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/data/export/CalendarEventMappingEntity.kt` - stable event mappings.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/ui/export/ScheduleExportBottomSheet.kt` - range and destination selection.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/ui/ScheduleFragment.kt` - root export action.

**Framework/Library Documentation:**

* `https://developer.android.com/identity/providers/calendar-provider` - Calendar Provider operations and permissions.

**Examples in existing code:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/schedule/domain/model/Lesson.kt` - event source fields.

**Verification commands:**

* `./gradlew :app:assembleDebug lintDebug`

### Stage 40: Add calendar export tests

**What to add/implement:**

* Test range boundaries, time zones, duplicate export, event updates, cancellations, revoked permissions, missing calendars, and stable deep-link descriptions.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/feature/schedule/data/export/CalendarExporterTest.kt` - export logic.
* `app/src/androidTest/java/dev/alllexey/itmowidgets/feature/schedule/ScheduleExportFlowTest.kt` - permission and UI flow.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

### Stage 41: Add verified App Links and sharing routes

**What to add/implement:**

* Add stable HTTPS routes for users, lesson occurrences, subjects, teachers, and sport lessons.
* Host Digital Asset Links metadata and a web fallback for users without the app.
* Preserve the requested route across authentication and reject inaccessible resources with a dedicated state.
* Add Android Sharesheet actions for profiles and lesson occurrences.

**Files to edit/create:**

* `../itmo-widgets-backend/src/main/kotlin/dev/alllexey/itmowidgets/backend/controllers/AppLinkController.kt` - safe fallback routes.
* `../itmo-widgets-backend/src/main/resources/static/.well-known/assetlinks.json` - Android association.
* `app/src/main/AndroidManifest.xml` - verified App Link intent filter.
* `app/src/main/java/dev/alllexey/itmowidgets/core/navigation/AppLinkRouter.kt` - route parsing and authorization continuation.
* `app/src/main/java/dev/alllexey/itmowidgets/core/navigation/ShareLinkFactory.kt` - canonical URLs.
* `app/src/main/java/dev/alllexey/itmowidgets/app/MainActivityIntentRouting.kt` - incoming App Link intents.

**Framework/Library Documentation:**

* `https://developer.android.com/training/app-links/about` - verified App Links.
* `https://developer.android.com/training/app-links/configure-assetlinks` - Digital Asset Links hosting.
* `https://developer.android.com/training/sharing/send` - Android Sharesheet.

**Examples in existing code:**

* `app/src/main/res/navigation/main_nav_graph.xml` - current destinations.

**Verification commands:**

* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ../itmo-widgets-backend/gradlew -p ../itmo-widgets-backend build`
* `./gradlew :app:assembleDebug lintDebug`

### Stage 42: Add App Link and sharing tests

**What to add/implement:**

* Test every route, malformed IDs, blocked profiles, private schedules, authentication continuation, app-not-installed fallback, and share payload.
* Verify domain association for debug and release signing certificates.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/core/navigation/AppLinkRouterTest.kt` - route tests.
* `app/src/androidTest/java/dev/alllexey/itmowidgets/core/navigation/AppLinkNavigationTest.kt` - intent tests.
* `../itmo-widgets-backend/src/test/kotlin/dev/alllexey/itmowidgets/backend/controllers/AppLinkControllerTest.kt` - fallback tests.

**Verification commands:**

* `adb shell pm verify-app-links --re-verify dev.alllexey.itmowidgets`
* `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`
* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ../itmo-widgets-backend/gradlew -p ../itmo-widgets-backend test`

### Stage 43: Integrate the smart home feed and final information architecture

**What to add/implement:**

* Populate the home feed with the current or next lesson, QR shortcut, unread schedule changes, unread BARS marks, subject attention, changed external score, sport queue actions, and moderation results.
* Show only relevant cards and remove empty decorative sections.
* Rename the recordbook root to the broader study concept while keeping the simple subject list.
* Preserve independent bottom-navigation state and hide the bottom bar on full-screen user, subject, teacher, review, and settings destinations.
* Add the QR quick-settings tile `QrTileService`: a tap requires an unlocked device and opens the QR pass through `MainActivityIntentRouting`; on Android 13+ the QR settings page offers `Добавить в шторку` through `StatusBarManager.requestAddTileService`.
* Add the static app shortcuts `QR-пропуск` and `Сегодня`; their intents use the same routing as widgets and notifications, queued until the session is signed in and consumed once.

**Files to edit/create:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/home/ui/HomeFeedAdapter.kt` - typed feed cards.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/home/ui/HomeFragment.kt` - feed collection and refresh.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/home/presentation/HomeViewModel.kt` - feed aggregation.
* `app/src/main/res/layout/fragment_home.xml` - final feed layout.
* `app/src/main/res/menu/bottom_nav.xml` - study naming.
* `app/src/main/res/navigation/main_nav_graph.xml` - final route hierarchy.
* `app/src/main/java/dev/alllexey/itmowidgets/app/MainActivity.kt` - bottom-bar visibility and back stacks.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/qr/ui/QrTileService.kt` - quick-settings tile.
* `app/src/main/res/xml/shortcuts.xml` - static shortcuts.
* `app/src/main/AndroidManifest.xml` - tile service with `BIND_QUICK_SETTINGS_TILE` and the shortcuts meta-data.
* `app/src/main/java/dev/alllexey/itmowidgets/app/MainActivityIntentRouting.kt` - shortcut and tile routes.
* `app/src/main/java/dev/alllexey/itmowidgets/feature/settings/presentation/SettingsViewModel.kt` - the `Добавить в шторку` action.

**Examples in existing code:**

* `app/src/main/java/dev/alllexey/itmowidgets/feature/sport/presentation/my/SportMyViewModel.kt` - aggregation of multiple repositories.

**Verification commands:**

* `./gradlew :app:assembleDebug lintDebug`

### Stage 44: Add home-feed and navigation regression tests

**What to add/implement:**

* Test card priority, irrelevant-card suppression, refresh errors, unread schedule changes, unread BARS marks, moderation results, selected subject resource changes, bottom-navigation state restoration, and deep-link back behavior.
* Test shortcut and tile intents: routing to the QR pass and today's schedule, queueing until sign-in, single consumption, a tile tap on a locked device requesting unlock, and the `Добавить в шторку` action hidden below Android 13.

**Files to edit/create:**

* `app/src/test/java/dev/alllexey/itmowidgets/feature/home/presentation/HomeViewModelTest.kt` - feed ordering tests.
* `app/src/androidTest/java/dev/alllexey/itmowidgets/app/MainNavigationTest.kt` - back-stack and bottom-bar tests.
* `app/src/test/java/dev/alllexey/itmowidgets/app/MainActivityIntentRoutingTest.kt` - shortcut and tile routing cases.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

### Stage 45: Complete documentation, compatibility checks, and release verification

**What to add/implement:**

* Document the user-facing feature set, privacy model, anonymous and named review behavior, moderation policy, external resource limitations, schedule sync guarantees, calendar permissions, and system map hand-off behavior.
* Update Core and Backend API documentation and version compatibility.
* Document Android/Core/Backend compatibility and the separately approved fresh MariaDB-to-PostgreSQL cutover after Android v2.1; clearly state which server-side records are reset and how to recover the old stack.

**Files to edit/create:**

* `README.md` - Android features and privacy.
* `../itmo-widgets-core/README.md` - typed API and compatibility.
* `../itmo-widgets-backend/README.md` - deployment, migrations, moderation, privacy, and importer.
* `docs/features/social.md` - public profile, friend, review, schedule, sport, and sheet-data rules.
* `docs/features/moderation.md` - moderation and appeal workflow.

**Verification commands:**

* `./gradlew :app:testDebugUnitTest :app:assembleRelease lintRelease`
* `JAVA_HOME=$(/usr/libexec/java_home -v 17) ../itmo-widgets-core/gradlew -p ../itmo-widgets-core clean test build`
* `JAVA_HOME=$(/usr/libexec/java_home -v 21) ../itmo-widgets-backend/gradlew -p ../itmo-widgets-backend clean test build`
* `git diff --check`
