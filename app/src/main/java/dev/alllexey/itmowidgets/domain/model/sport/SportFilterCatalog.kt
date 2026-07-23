package dev.alllexey.itmowidgets.domain.model.sport

data class SportFilterCatalog(
    val buildings: List<SportFilterOption>,
    val sections: List<SportFilterOption>,
    val sportTypes: List<SportFilterOption>,
    val teachers: List<SportFilterOption>
)

data class SportFilterOption(
    val id: Long,
    val value: String
)

data class SportTimeSlot(
    val id: Long,
    val start: String,
    val end: String
) {
    val displayName: String = "$start-$end"
}
