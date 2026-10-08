import Shared

extension Routes {
    /// The `.ics` export has no route on iOS (IO-15b): the settings calendar group presents `IcsExportSheet` itself on
    /// `SettingsEvent.OpenIcsExport`, the event Android's settings answer too, so nothing opens this key.
    static func calendar(_ route: any AppRoute) -> RouteTarget {
        .notOnIOS
    }
}
