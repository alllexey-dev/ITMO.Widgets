package dev.alllexey.itmowidgets.ios

/**
 * The first symbol the Swift shell reads from the `Shared` framework (L18 IO-02): proves the link from Xcode's
 * build phase to this module. IO-05 replaces it with the shell entry points.
 */
object IosShell {
    /** The product name, not a translatable text: user-visible Russian strings come from the catalog. */
    val productName: String = "ITMO.Widgets"
}
