package dev.alllexey.itmowidgets.core.navigation

/** Arguments of the contextual screens that show another user's data. */
object UserScreenArgs {
    const val ISU = "user_isu"
    const val NAME = "user_name"
    const val GIVEN_NAME = "user_given_name"

    fun profileIsu(isu: Long?): Int? = isu?.takeIf { it in 1..Int.MAX_VALUE }?.toInt()
}
