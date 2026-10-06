# Profile tab

- The Me tab is Compose in `:shared:feature-account` (`MeScreen`,
  `MeRoute`), hosted by the same `MeFragment`; `fragment_me.xml` and
  `MeRenderer` are gone, and the Me strings are no longer Android resources.
- Sign-out asks in the design system's confirmation dialog; on iOS it is an
  alert with «Выйти» in red.
- The same `MeRoute` is ready for the iOS app: the screen holds no Android
  type, and links, sharing and navigation come from the host.
