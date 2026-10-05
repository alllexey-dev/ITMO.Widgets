package dev.alllexey.itmowidgets.core.text

sealed interface UiText {

    data class Resource(
        val resourceId: Int,
        val arguments: List<Any> = emptyList()
    ) : UiText

    data class Dynamic(val value: String) : UiText
}
