package dev.alllexey.itmowidgets.feature.social.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.social.domain.model.Person

/** My ITMO directory queried on the device without the Backend opt-in; contacts never leave the data layer. */
interface PersonRepository {
    fun cachedPerson(isu: Int): Person?
    suspend fun person(isu: Int): AppResult<Person>
}
