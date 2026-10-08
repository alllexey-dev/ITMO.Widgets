# shared/feature-reviews

## Owns
- `commonMain`, packages `dev.alllexey.itmowidgets.feature.reviews.*`: teacher reviews and levels. `data/`
  (`TeacherReviewsRepositoryImpl`, `TeacherLevelsRepositoryImpl`, `TeacherLevelsFileStore`, mappers, errors,
  `demo/`), `presentation/` (`ReviewEditorViewModel`, `ReportReviewViewModel`), `ui/` (`ReviewEditorSheet`,
  `ReportReviewDialog`).
- `di/ReviewsKoinModule.kt`: the Koin module `reviewsModule`; `androidMain`, `iosMain`: the file system actual
  (`ReviewsFileSystem`) only.
- `strings_reviews.xml` in `composeResources/values/`, exported to `:app` as Android resources.
- `commonTest`: repositories, store and ViewModels over `ReviewsBackendHarness`; `androidHostTest`:
  `ReviewsKoinModuleTest`, editor and report tests, `ReviewsScreenshotTest`.
- Not here: the review rows and summary card of a profile (`:shared:feature-social`, `ui/reviews/`), and
  `ReviewEditorBottomSheet`, `ReportReviewDialogFragment` in `app/` under `feature/reviews/`.

## Depends on
- `:shared:core`, `:shared:designsystem`; `:shared:testing` in tests only. Koin and the KMP lifecycle.
- Backend's `reviews` area behind `BackendGate`; nothing from MyItmoApi.

## Verify
`scripts/verify.sh quick`, then `scripts/verify.sh shots feature-reviews`; `scripts/verify.sh klibs
feature-reviews` after an `iosMain` change.

## Hot files
- Every file here: lane L15; `iosMain`: lane L18. `build.gradle.kts`: L15 writes, L04 reviews.
- `strings_reviews.xml`: L15; keys are never renamed.
- Stable identifiers held here: `filesDir/teacher_levels/levels.json` (also a backup exclusion).
- `ReviewsRulesTest` (Konsist, lane L06): the shared reviews code imports no other feature.

## Docs
- [Reviews](../../docs/features/reviews.md) ([teacher levels](../../docs/features/reviews.md#teacher-levels)).
- ADRs [0008](../../docs/decisions/0008-community-moderation.md) (moderation),
  [0010](../../docs/decisions/0010-review-verification.md) (verification) and
  [0011](../../docs/decisions/0011-ai-review-summaries.md) (AI summaries).
