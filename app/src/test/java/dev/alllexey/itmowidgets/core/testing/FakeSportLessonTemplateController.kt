package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateController

class FakeSportLessonTemplateController : SportLessonTemplateController {
    private var enabled = false

    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
