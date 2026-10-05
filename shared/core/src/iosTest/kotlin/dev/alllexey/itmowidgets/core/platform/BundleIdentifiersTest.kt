package dev.alllexey.itmowidgets.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BundleIdentifiersTest {

    @Test
    fun triesTheBuiltGroupBeforeAltStoreGroups() {
        val identifiers = BundleIdentifiers(
            appGroupId = "group.example.app",
            keychainGroup = null,
            altStoreAppGroups = listOf("group.example.app.TEAM", "group.example.app")
        )

        assertEquals(listOf("group.example.app", "group.example.app.TEAM"), identifiers.appGroupCandidates)
    }

    @Test
    fun aProcessWithoutTheInfoPlistKeysHasNoIdentifiers() {
        // The Kotlin/Native test binary has no app Info.plist, as a misconfigured target would not.
        val identifiers = BundleIdentifiers.fromMainBundle()

        assertNull(identifiers.appGroupId)
        assertNull(identifiers.keychainGroup)
        assertEquals(emptyList(), identifiers.appGroupCandidates)
    }
}
