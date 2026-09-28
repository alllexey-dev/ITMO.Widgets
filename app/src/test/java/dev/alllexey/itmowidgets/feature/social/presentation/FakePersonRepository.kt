package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.domain.model.Person

internal class FakePersonRepository : PersonRepository {
    var people: Map<Int, AppResult<Person>> = emptyMap()
    var cached: Map<Int, Person> = emptyMap()
    var gate: suspend () -> Unit = {}
    var calls = 0
        private set

    override fun cachedPerson(isu: Int): Person? = cached[isu]

    override suspend fun person(isu: Int): AppResult<Person> {
        calls += 1
        gate()
        return people[isu] ?: AppResult.Failure(AppError.NotFound)
    }
}

internal fun samplePerson(isu: Int) = Person(isu, "Персона $isu", null, emptyList(), emptyList(), emptyList())
