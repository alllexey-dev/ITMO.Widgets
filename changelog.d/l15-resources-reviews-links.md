# Subject links

- The links sheet, the link editor, the link actions and the report dialog are
  Compose Multiplatform in `:shared:feature-resources` inside the unchanged
  `SubjectLinksBottomSheet`, `LinkEditorBottomSheet`, `LinkActionsBottomSheet`
  and `ReportLinkDialogFragment` hosts, on the kit's sheet host, vote pill,
  confirm and report dialogs; their XML layouts, the debug preview host and the
  instrumented `SubjectLinksVisualTest` are gone, replaced by JVM tests and
  four-appearance goldens.
- Deleting an own link asks first in the kit's confirm dialog, which survives
  rotation. The report dialog keeps its reason and comment across rotation,
  cannot be dismissed while a report is being sent and shows a failure under
  the comment.
