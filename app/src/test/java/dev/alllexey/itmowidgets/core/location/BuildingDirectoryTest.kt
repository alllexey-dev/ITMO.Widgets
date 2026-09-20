package dev.alllexey.itmowidgets.core.location

import com.google.gson.Gson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildingDirectoryTest {
    private val directory = BuildingDirectory.parse(
        File("src/main/res/raw/itmo_buildings.json").readText(),
        Gson()
    )

    @Test
    fun `the shipped directory lists every known campus with coordinates in Saint Petersburg`() {
        assertEquals(
            listOf("kronva", "lomo", "vyazma", "griva", "birzha", "pesochka", "chaika"),
            directory.all.map { it.id }
        )
        directory.all.forEach { building ->
            assertTrue(building.id, building.latitude in 59.85..60.05)
            assertTrue(building.id, building.longitude in 30.15..30.45)
            assertTrue(building.id, building.aliases.isNotEmpty() && building.label.isNotBlank())
        }
    }

    @Test
    fun `main building id wins over the room building id and over the name`() {
        val kronva = directory.find(buildingId = 273, mainBuildingId = 13, buildingName = "ул. Ломоносова, 9")
        assertEquals("kronva", kronva?.id)
        assertEquals("lomo", directory.find(buildingId = 273, mainBuildingId = null, buildingName = null)?.id)
    }

    @Test
    fun `names resolve through lower-case aliases when no id is known`() {
        assertEquals("griva", directory.find(null, null, "ПЕР. ГРИВЦОВА, 14-16")?.id)
        assertEquals("chaika", directory.find(null, null, "ул. Чайковского, 11/2")?.id)
        assertEquals("pesochka", directory.find(null, null, "Песочная наб., 14")?.id)
    }

    @Test
    fun `unknown ids and names resolve to nothing`() {
        assertNull(directory.find(999, 319, "Виртуальные аудитории"))
        assertNull(directory.find(null, null, "  "))
        assertNull(directory.find(null, null, null))
    }

    @Test
    fun `a directory with duplicate ids is rejected at construction`() {
        val one = directory.all.first()
        assertThrows(IllegalArgumentException::class.java) { BuildingDirectory(listOf(one, one)) }
    }
}
