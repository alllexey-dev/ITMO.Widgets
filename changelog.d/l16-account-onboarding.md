# First-run flow

- The first-run flow is Compose in `:shared:feature-account`
  (`OnboardingScreen`), hosted by the same `OnboardingFragment`; the widget
  previews are the same `WidgetPreviewFactory` views in an `AndroidView`
  slot. The step Fragments, `OnboardingStepsView`, `OnboardingStepAdapter`
  and the four `fragment_onboarding*.xml` layouts are gone, and the
  onboarding strings are no longer Android resources.
- Steps, rows, «Пропустить», «Далее» and «Готово», the launcher pin, the
  notification permission and the spoiler image picker work as before.
