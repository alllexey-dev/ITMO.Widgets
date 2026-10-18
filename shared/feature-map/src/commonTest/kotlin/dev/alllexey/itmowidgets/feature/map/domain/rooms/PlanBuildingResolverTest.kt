package dev.alllexey.itmowidgets.feature.map.domain.rooms

import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.location.KnownBuilding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanBuildingResolverTest {
    private val resolver = PlanBuildingResolver(
        BuildingDirectory(
            listOf(
                KnownBuilding("griva", emptyList(), listOf("гривц"), "пер. Гривцова, 14-16", "", 59.9, 30.3, planBuildingId = 279),
                KnownBuilding("vyazma", listOf(5), listOf("вязем"), "Вяземский пер., 5-7", "", 59.9, 30.3),
            ),
        ),
    )

    @Test
    fun theMainBuildingIdIsThePlanIdForEveryLomonosovaLetter() {
        assertEquals(273, resolver.resolve(mainBuildingId = 273, buildingId = 37, "ул.Ломоносова, д.9, лит. М", PLANNED))
    }

    @Test
    fun theBuildingIdServesWhenTheMainOneIsMissing() {
        assertEquals(13, resolver.resolve(mainBuildingId = null, buildingId = 13, null, PLANNED))
    }

    @Test
    fun theDirectoryAliasServesWithoutIds() {
        assertEquals(279, resolver.resolve(null, null, "пер. Гривцова, д.14-16, лит.А", PLANNED))
    }

    @Test
    fun aBuildingWithoutAPlanResolvesToNothing() {
        assertNull(resolver.resolve(mainBuildingId = 5, buildingId = 5, "Вяземский пер., д.5-7, лит.А", PLANNED))
        assertNull(resolver.resolve(null, null, "пер. Гривцова, 14", setOf(13)))
    }

    private companion object {
        val PLANNED = setOf(13, 273, 279)
    }
}
