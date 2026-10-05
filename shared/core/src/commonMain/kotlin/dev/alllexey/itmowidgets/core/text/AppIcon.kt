package dev.alllexey.itmowidgets.core.text

/**
 * An icon that presentation state names without a platform resource. [id] is the row of `docs/design/icons.tsv`
 * (DS-06a) and the drawable `ic_<id>`; Views map it in `core/ui/AppIconRes.kt`, Swift through the registry's SF
 * Symbol. Add a constant when presentation first needs the icon.
 */
enum class AppIcon(val id: String) {
    CHEVRON_RIGHT("chevron_right"),
    DOWNLOAD("download"),
    OPEN_IN_NEW("open_in_new"),
    REFRESH("refresh")
}
