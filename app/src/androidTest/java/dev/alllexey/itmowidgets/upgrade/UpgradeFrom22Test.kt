package dev.alllexey.itmowidgets.upgrade

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.upgrade.stores.AppPreferencesUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.CalendarSyncFileStoreUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.CustomSpoilerUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.DebugOverrideUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.DiagnosticsLogUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.MarksFileStoreUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.QrCacheUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.ScheduleCacheUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.ScheduleChangesFileStoreUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.ScheduleWidgetSnapshotUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.SheetScoresFileStoreUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.SubjectLinksFileStoreUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.TeacherLevelsFileStoreUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.TeacherWeeksFileStoreUpgrade
import dev.alllexey.itmowidgets.upgrade.stores.TokenFilesUpgrade
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A 2.2 installation upgraded to head keeps its data: head stores read the data directory captured at tag `v2.2`
 * (`assets/upgrade-2.2`, see its README). One test per store; a lane that moves or reformats a store edits only its
 * checker in `stores/` (recipe `upgrade-fixture`), never the assets.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeFrom22Test {

    private lateinit var fixture: Upgrade22Fixture

    @Before
    fun copyThe22DataDirectory() {
        fixture = Upgrade22Fixture()
    }

    @After
    fun removeTheCopy() {
        fixture.close()
    }

    /** The assets are the 2.2 capture byte for byte; checkers change, the capture never does. */
    @Test
    fun fixtureIsTheUnchanged22Capture() {
        val actual = fixture.assetPaths().associateWith { path ->
            MessageDigest.getInstance("SHA-256").digest(fixture.assetBytes(path)).joinToString("") { "%02x".format(it) }
        }
        assertEquals(CAPTURED_SHA256, actual)
    }

    @Test fun tokenFiles() = TokenFilesUpgrade.check(fixture)

    @Test fun appPreferences() = AppPreferencesUpgrade.check(fixture)

    @Test fun debugOverrides() = DebugOverrideUpgrade.check(fixture)

    @Test fun marksFileStore() = MarksFileStoreUpgrade.check(fixture)

    @Test fun sheetScoresFileStore() = SheetScoresFileStoreUpgrade.check(fixture)

    @Test fun scheduleChangesFileStore() = ScheduleChangesFileStoreUpgrade.check(fixture)

    @Test fun calendarSyncFileStore() = CalendarSyncFileStoreUpgrade.check(fixture)

    @Test fun subjectLinksFileStore() = SubjectLinksFileStoreUpgrade.check(fixture)

    @Test fun teacherLevelsFileStore() = TeacherLevelsFileStoreUpgrade.check(fixture)

    @Test fun teacherWeeksFileStore() = TeacherWeeksFileStoreUpgrade.check(fixture)

    @Test fun scheduleWidgetSnapshot() = ScheduleWidgetSnapshotUpgrade.check(fixture)

    @Test fun scheduleCache() = ScheduleCacheUpgrade.check(fixture)

    @Test fun qrCaches() = QrCacheUpgrade.check(fixture)

    @Test fun customSpoiler() = CustomSpoilerUpgrade.check(fixture)

    @Test fun diagnosticsLog() = DiagnosticsLogUpgrade.check(fixture)

    private companion object {
        val CAPTURED_SHA256 = mapOf(
            "cache/bitmap_cache/noise/c9917a61cb6746ed178bea252f3622699cc0cf63bcf6f0712b68e038dc7dcad5.png" to
                "15ff44546d851c324ce0ba844a494075ddf32603e1eafe28a15b52dafa722bbd",
            "cache/qr_hex" to "296a8f51264e3cfb187c57cc4285cc5b8bc08704abb0128c115d3efdfa84728c",
            "cache/schedule_cache/123456_2026-10-05.json" to
                "3cba9ed05c356733e5cd3b3a66b55e6c1bab81c439871b2882283f96db9c1455",
            "files/calendar_sync/state.json" to "663acd3b4da08d73dbdf71f97967f7624355e78f162c6a27ba077a115306a912",
            "files/datastore/app_preferences.preferences_pb" to
                "46e58960c86611ac6fff69529129be0d8ae3c1dcc85d794ffed3e293c7a05de0",
            "files/diagnostics/log.jsonl" to "f79991ba890fac4408aee0311b7e1a39597a1d45049f079cf45a3b2ed01fd462",
            "files/marks/state.json" to "ca7046b92a7e0baf92a7ce7a140837e9708224127296960f246e4e762ad51761",
            "files/qr_custom_spoiler/custom_spoiler.png" to
                "ade2a8bb97cd29747565a1ff496bd5edef6be737a1649060d608e0a4a3c6d80a",
            "files/schedule_changes/state.json" to "c4798c8e7e9e828213340c2e1482b5a40cb373532a232261b88ca054ed38cc09",
            "files/sheet_scores/state.json" to "dcdf06a59b93ac3545bb0027ac80c4e920f6b1eded19e5de229ef27fcc4af4a2",
            "files/subject_links/cache.json" to "02924c78c5e3f5f0beed5ea25a95b03bbba49d89b7095aeeef8b2f385eccc167",
            "files/teacher_lessons/weeks.json" to "b3d724ac7ebc978d06ae6a239ad02a1447900c3b2e0eb5b2384d7470f13d25b1",
            "files/teacher_levels/levels.json" to "14f81d1060abd360045c2d84548ca1c74c55300219706d0b69fb4ff7fb423afd",
            "no_backup/debug/academic_date_override" to
                "04c17ac53fcbb2057b867357a095d4d5c703f6a4414cae8aec5161cf42f0ccf0",
            "no_backup/debug/sport_lesson_templates" to
                "b5bea41b6c623f7c09f1bf24dcae58ebab3c0cdd90ad966bc43a45b44867e12b",
            "no_backup/debug/sport_score_override" to "9ca29a28a8e8cac54393c23a78605b42d10325a6237178fa052b814ca3421fda",
            "no_backup/widgets/schedule_snapshot.json" to
                "5b6888e850c52372aed38870f48617d9ae7f8f65a704aa4a8a4071d49dac065d",
            "plaintext/bars_tokens.enc" to "8f963e5a51e895a3dd77d6afc6dad987d60f091fcb9153f02dcffcbc152e5172",
            "plaintext/myitmo_tokens.enc" to "df13d1f9325874794fb3f754c64439330b1c64bf8424f8f7ab7f3444b3f1cccf"
        )
    }
}
