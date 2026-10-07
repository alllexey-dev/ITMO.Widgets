# Teacher reviews

- The review editor and the report dialog are Compose Multiplatform in
  `:shared:feature-reviews` (`ReviewEditorSheet`, `ReportReviewForm`) inside
  the unchanged `ReviewEditorBottomSheet` and `ReportReviewDialogFragment`
  hosts, on the kit's sheet host, confirm and report dialogs; their XML
  layouts, the debug preview host and the instrumented `ReviewEditorVisualTest`
  are gone, replaced by JVM tests and four-appearance goldens.
- The editor's `Отправить`/`Сохранить` stays pinned above the keyboard and
  shows a progress indicator while saving; restored text keeps the cursor at
  its end. The report dialog keeps its reason and comment across rotation and
  cannot be dismissed while a report is being sent.
