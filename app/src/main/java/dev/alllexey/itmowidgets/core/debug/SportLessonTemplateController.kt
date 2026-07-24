package dev.alllexey.itmowidgets.core.debug

interface SportLessonTemplateController {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}

interface SportLessonTemplateStore {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean)
}

class DefaultSportLessonTemplateController(
    private val store: SportLessonTemplateStore
) : SportLessonTemplateController {

    override fun isEnabled(): Boolean = store.isEnabled()

    override fun setEnabled(enabled: Boolean) {
        store.setEnabled(enabled)
    }
}
