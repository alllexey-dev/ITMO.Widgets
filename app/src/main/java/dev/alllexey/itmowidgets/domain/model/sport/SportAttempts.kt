package dev.alllexey.itmowidgets.domain.model.sport

data class SportAttempts(
    val total: Int,
    val used: Int,
    val free: Int,
    val canSignIn: Boolean
)
