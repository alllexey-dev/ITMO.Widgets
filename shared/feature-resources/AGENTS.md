# shared/feature-resources

## Owns
- `commonMain`, packages `dev.alllexey.itmowidgets.feature.resources.*`: a subject's shared links. `data/`
  (`SubjectLinksRepositoryImpl`, `SubjectLinksFileStore`, mappers, errors, `demo/`), `domain/` (`LinkCategoryGuess`),
  `presentation/` (`SubjectLinksViewModel`, `LinkEditorViewModel`, `LinkEvent`), `ui/` (`SubjectLinksSheet`,
  `LinkEditorSheet`, `LinkActionsSheet`, `ReportLinkDialog`, rows, captions, previews).
- `di/ResourcesKoinModule.kt`: the Koin module `resourcesModule`; `androidMain`, `iosMain`: the file system actual
  (`ResourcesFileSystem`) only.
- `strings_resources.xml` in `composeResources/values/`, exported to `:app` as Android resources.
- `commonTest`: repository, store, ViewModels and rows over `LinksBackendHarness`; `androidHostTest`: the sheets
  and dialog, `ResourcesKoinModuleTest`, `ResourcesScreenshotTest`.
- Not here: the bottom sheet and dialog hosts (`SubjectLinksBottomSheet`, `LinkEditorBottomSheet`,
  `LinkActionsBottomSheet`, `ReportLinkDialogFragment`) stay in `app/` under `feature/resources/`; the subject page
  that opens them is in `:shared:feature-recordbook`.

## Depends on
- `:shared:core`, `:shared:designsystem`; `:shared:testing` in tests only. Koin and the KMP lifecycle.
- Backend's `links` area behind `BackendGate`; nothing from MyItmoApi.

## Verify
`scripts/verify.sh quick`, then `scripts/verify.sh shots feature-resources`; `scripts/verify.sh klibs
feature-resources` after an `iosMain` change.

## Hot files
- Every file here: lane L15; `iosMain`: lane L18. `build.gradle.kts`: L15 writes, L04 reviews.
- `strings_resources.xml`: L15; keys are never renamed.
- Stable identifiers held here: `filesDir/subject_links/cache.json` (also a backup exclusion).
- `ResourcesRulesTest` (Konsist, lane L06): the shared links code imports no other feature.

## Docs
- [Resources](../../docs/features/resources.md),
  its [storage and synchronization](../../docs/features/resources.md#storage-and-synchronization).
- [Endpoint end to end](../../docs/recipes/endpoint-end-to-end.md); ADR
  [0008](../../docs/decisions/0008-community-moderation.md) (community moderation).
