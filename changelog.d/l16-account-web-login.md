# Web sign-in

- The «Вход на сайт» sheet is Compose in `:shared:feature-account`
  (`WebLoginSheetContent`, `WebLoginSheetRoute`), hosted by the same
  `WebLoginBottomSheet` on the design system's sheet host; `sheet_web_login.xml`
  and its debug host are gone, and the web sign-in strings are no longer
  Android resources.
- The steps, texts, the QR scanner and the keyboard's «Go» stay as they were;
  the scanner icon now sits next to its label, and the progress takes the
  place of «Продолжить» and «Войти» while they run.
