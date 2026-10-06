package dev.alllexey.itmowidgets.core.navigation

import androidx.navigation3.runtime.NavKey

/**
 * A destination of the shell. Every key is `@Serializable` and joins one polymorphic module
 * ([appRouteSerializersModule]), so feature modules can add their own keys and the back stacks survive process death;
 * non-JVM targets have no reflection, which is why the registration is explicit.
 *
 * The key alone tells the shell how to show it: [kind] picks the layer, [sheetPolicy] the sheet's behaviour, and
 * [exclusiveGroup] names keys that are never shown at the same time.
 */
interface AppRoute : NavKey {
    val kind: RouteKind

    /** How a [RouteKind.SHEET] opens and closes; null for every other kind. */
    val sheetPolicy: SheetPolicy? get() = null

    /** Sheets and dialogs of one group replace nothing and open only while none of the group is shown. */
    val exclusiveGroup: String? get() = null
}

/** The layer a key is shown in. */
enum class RouteKind {
    /** A full-window surface without the bar (sign-in, first-run flow), chosen by [ShellGate], never pushed. */
    GATE,

    /** The root of a bottom tab, under the bar. */
    TAB_ROOT,

    /** A contextual screen in the overlay stack: full window above the bar, which keeps its size. */
    SCREEN,

    /** A bottom sheet above everything; [AppRoute.sheetPolicy] says how. */
    SHEET,

    /** An alert dialog above everything. */
    DIALOG,
}

/**
 * The design system's `SheetSpec` in common code: the shell maps it to the sheet scene, iOS to detents and
 * `interactiveDismissDisabled`.
 */
data class SheetPolicy(
    val height: Height = Height.FIT_CONTENT,
    val dismissal: Dismissal = Dismissal.FREE,
    val textInput: Boolean = false,
) {
    enum class Height {
        /** As tall as the content, up to the kit's cap. */
        FIT_CONTENT,

        /** Always the kit's tall height: the details sheets and the friend selector. */
        TALL,
    }

    enum class Dismissal {
        /** Drag, Back and a tap outside close it. */
        FREE,

        /** A form that must not lose input: no drag, no tap outside, Back goes to the sheet's own question. */
        FORM,
    }
}
