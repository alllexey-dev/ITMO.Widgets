package dev.alllexey.itmowidgets.core.platform

import dev.alllexey.itmowidgets.core.storage.TemporaryDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.of
import okio.FileSystem

class BundleIdentifiersTest {

    private val temporary = TemporaryDirectory()

    @AfterTest
    fun deleteTemporary() = temporary.delete()

    @Test
    fun triesTheBuiltGroupBeforeAltStoreGroups() {
        val identifiers = BundleIdentifiers(
            appGroupId = "group.example.app",
            keychainGroup = null,
            altStoreAppGroups = listOf("group.example.app.TEAM", "group.example.app")
        )

        assertEquals(listOf("group.example.app", "group.example.app.TEAM"), identifiers.appGroupCandidates)
    }

    /** SideStore re-signs the group into `<group>.<TEAMID>` and writes no `ALTAppGroups`: only the profile names it. */
    @Test
    fun triesTheGroupsOfTheProvisioningProfileLast() {
        val profile = temporary.root / "embedded.mobileprovision"
        FileSystem.SYSTEM.write(profile) {
            // The CMS envelope around the plist: binary bytes before and after it.
            write(of(0x30, 0x82.toByte(), 0x3c, 0x00, 0xff.toByte()))
            write(
                """<?xml version="1.0" encoding="UTF-8"?>
                <plist version="1.0"><dict>
                <key>TeamIdentifier</key><array><string>TEAMID</string></array>
                <key>Entitlements</key><dict>
                <key>com.apple.security.application-groups</key><array><string>group.example.app.TEAMID</string></array>
                </dict></dict></plist>""".encodeUtf8()
            )
            write(of(0xa0.toByte(), 0x82.toByte(), 0x00))
        }
        val identifiers = BundleIdentifiers(
            appGroupId = "group.example.app",
            keychainGroup = null,
            provisionedAppGroups = BundleIdentifiers.provisionedAppGroups(profile.toString())
        )

        assertEquals(listOf("group.example.app", "group.example.app.TEAMID"), identifiers.appGroupCandidates)
    }

    @Test
    fun anUnreadableProfileGrantsNoGroups() {
        val garbage = temporary.root / "garbage.mobileprovision"
        FileSystem.SYSTEM.write(garbage) { writeUtf8("not a profile") }

        assertEquals(emptyList(), BundleIdentifiers.provisionedAppGroups(garbage.toString()))
        assertEquals(emptyList(), BundleIdentifiers.provisionedAppGroups((temporary.root / "missing").toString()))
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
