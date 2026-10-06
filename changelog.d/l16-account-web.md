# My ITMO in the app

- The My ITMO page is Compose in `:shared:feature-account`
  (`MyItmoWebScreen`) around the same WebView, hosted by the same
  `MyItmoWebFragment` through `AndroidView`; `fragment_my_itmo_web.xml` and
  `my_itmo_web_menu.xml` are gone, and the web strings are no longer Android
  resources.
- The top bar no longer shows the current site under «My ITMO»; the
  progress line, the error page with «Повторить», Back through the web
  history and the restored history after recreation stay as they were.
