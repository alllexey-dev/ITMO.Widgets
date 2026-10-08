# Recordbook

- The recordbook screens are Compose Multiplatform in `:shared:feature-recordbook`: the list,
  the period sheet, the subject page and the «Мои баллы» sheet, hosted by the same
  Fragments and sheets. No behaviour change.
- The subject page's sections, states and snackbars are checked by JVM host tests and
  Roborazzi goldens in all four appearances instead of instrumented visual tests.
