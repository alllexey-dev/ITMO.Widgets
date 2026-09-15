package dev.alllexey.itmowidgets.feature.friendselector

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorAdapter
import dev.alllexey.itmowidgets.feature.friendselector.ui.RecentFriendAdapter
import dev.alllexey.itmowidgets.feature.friendselector.ui.RecentFriendItem
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import dev.alllexey.itmowidgets.feature.sport.ui.sign.MultiSelectSearchableAdapter
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SelectableItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionRowsTest {

    @Test
    fun friendSelectionAndAvailabilityAreExposedOnTheWholeRowAfterRebinding() = preview { scenario ->
        scenario.onActivity { activity ->
            val friend = friend()
            val clicks = mutableListOf<UserSummary>()
            val adapter = FriendSelectorAdapter(friend.isu, clicks::add, onOpenProfile = {})
            val holder = adapter.onCreateViewHolder(activity.sectionsContainer, 0)
            val row = holder.itemView as MaterialCardView
            activity.sectionsContainer.addView(row)

            holder.bind(friend)
            assertChoice(row, checked = true)
            assertTrue(row.contentDescription.contains(friend.name))
            assertTrue(row.strokeWidth > 0)
            assertEquals(View.VISIBLE, row.findViewById<View>(R.id.trailing_icon).visibility)
            row.performClick()
            assertEquals(listOf(friend), clicks)

            holder.bind(friend.copy(sharing = UserSharing(sport = true, schedule = false)))
            assertChoice(row, checked = false, selectable = false)
            assertFalse(row.isEnabled)
            assertFalse(row.isClickable)
            assertEquals(0, row.strokeWidth)
            assertTrue(row.contentDescription.contains(activity.getString(R.string.friend_picker_schedule_hidden)))
            row.performClick()
            assertEquals(1, clicks.size)

            adapter.setSelectedIsu(null)
            holder.bind(friend)
            assertChoice(row, checked = false)
            assertTrue(row.isEnabled)
            assertEquals(1f, row.alpha, 0f)
            assertEquals(View.INVISIBLE, row.findViewById<View>(R.id.trailing_icon).visibility)

            adapter.setSelectedIsu(friend.isu)
            holder.bind(friend)
            assertChoice(row, checked = true)
        }
    }

    @Test
    fun recentChoicesAnnounceFullIdentityAndExcludePrivateSchedules() = preview { scenario ->
        scenario.onActivity { activity ->
            val friend = friend()
            val adapter = RecentFriendAdapter {}
            adapter.submitItems(
                listOf(friend, friend.copy(isu = 900002, sharing = UserSharing(true, false))),
                friend.isu,
                null
            )
            assertEquals(2, adapter.itemCount)
            val holder = adapter.onCreateViewHolder(activity.sectionsContainer, 0)
            activity.sectionsContainer.addView(holder.itemView)
            adapter.onBindViewHolder(holder, 1)
            assertChoice(holder.itemView, checked = true)
            assertEquals(friend.name, holder.itemView.contentDescription)

            adapter.submitItems(listOf(friend), null, null)
            holder.bind(RecentFriendItem.MySchedule)
            assertChoice(holder.itemView, checked = true)
            assertEquals(
                activity.getString(R.string.friend_picker_my_schedule_accessibility),
                holder.itemView.contentDescription
            )
            holder.bind(RecentFriendItem.Friend(friend))
            assertChoice(holder.itemView, checked = false)
        }
    }

    @Test
    fun sportFilterSelectionSurvivesFilteringAndRecycledRowsClearTheSelection() = preview { scenario ->
        scenario.onActivity { activity ->
            val items = listOf(SelectableItem("Плавание"), SelectableItem("Танцы"))
            val adapter = MultiSelectSearchableAdapter(items)
            val holder = adapter.onCreateViewHolder(activity.sectionsContainer, 0)
            activity.sectionsContainer.addView(holder.itemView)
            adapter.onBindViewHolder(holder, 0)
            assertChoice(holder.itemView, checked = false, multiple = true)

            holder.itemView.performClick()
            assertChoice(holder.itemView, checked = true, multiple = true)
            assertTrue(holder.itemView.findViewById<MaterialCheckBox>(R.id.item_checkbox).isChecked)
            assertEquals(listOf(items.first()), adapter.getSelectedItems())

            adapter.filter("Танцы")
            adapter.onBindViewHolder(holder, 0)
            assertChoice(holder.itemView, checked = false, multiple = true)
            assertFalse(holder.itemView.findViewById<MaterialCheckBox>(R.id.item_checkbox).isChecked)
            adapter.filter(null)
            adapter.onBindViewHolder(holder, 0)
            assertChoice(holder.itemView, checked = true, multiple = true)
        }
    }

    @Test
    fun longNamesAndFilterTargetsFitNarrowLightDarkAndDynamicPalettes() {
        for (dark in listOf(false, true)) {
            for (fontScale in listOf(1f, 1.3f)) {
                preview(fontScale, dark, 0xff087f5b.toInt()) { scenario ->
                    scenario.onActivity { activity ->
                        val friendAdapter = FriendSelectorAdapter(onClick = {}, onOpenProfile = {})
                        val friendHolder = friendAdapter.onCreateViewHolder(activity.sectionsContainer, 0)
                        friendHolder.bind(friend())
                        activity.sectionsContainer.addView(friendHolder.itemView)
                        val filterAdapter = MultiSelectSearchableAdapter(listOf(SelectableItem(LONG_SPORT)))
                        val filterHolder = filterAdapter.onCreateViewHolder(activity.sectionsContainer, 0)
                        filterAdapter.onBindViewHolder(filterHolder, 0)
                        activity.sectionsContainer.addView(filterHolder.itemView)
                    }
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    scenario.onActivity { activity ->
                        val container = activity.sectionsContainer
                        for (index in 0 until container.childCount) {
                            val row = container.getChildAt(index)
                            assertTrue(row.height >= 48 * activity.resources.displayMetrics.density)
                            assertTrue(row.width >= 48 * activity.resources.displayMetrics.density)
                        }
                        for (id in listOf(R.id.name, R.id.sharing_status, R.id.item_name_text_view)) {
                            val text = container.findViewById<TextView>(id)
                            assertTrue(text.layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                            for (line in 0 until text.lineCount) assertEquals(0, text.layout.getEllipsisCount(line))
                        }
                        val title = container.findViewById<TextView>(R.id.name)
                        assertTrue(title.lineCount > 1)
                        val group = title.parent as ViewGroup
                        val content = group.parent as ViewGroup
                        assertEquals(content.paddingTop, content.paddingBottom)
                        val topSpace = group.top - content.paddingTop
                        val bottomSpace = content.height - content.paddingBottom - group.bottom
                        assertTrue(kotlin.math.abs(topSpace - bottomSpace) <= 1)
                        val filterTitle = container.findViewById<TextView>(R.id.item_name_text_view)
                        assertEquals(
                            MaterialColors.getColor(filterTitle, com.google.android.material.R.attr.colorOnSurface),
                            filterTitle.currentTextColor
                        )
                    }
                    screenshot("selection-${if (dark) "dark" else "light"}-font${(fontScale * 100).toInt()}")
                }
            }
        }
    }

    private fun assertChoice(row: View, checked: Boolean, selectable: Boolean = true, multiple: Boolean = false) {
        val info = row.createAccessibilityNodeInfo()
        assertEquals(selectable, info.isCheckable)
        assertEquals(checked, info.isChecked)
        assertEquals(checked, info.isSelected)
        assertEquals(checked, row.isSelected)
        assertEquals(
            when {
                row is MaterialCardView -> "androidx.cardview.widget.CardView"
                !selectable -> "android.view.View"
                multiple -> "android.widget.CheckBox"
                else -> "android.widget.RadioButton"
            },
            info.className.toString()
        )
        if (row is MaterialCardView) {
            assertEquals(selectable, row.isCheckable)
            assertEquals(checked, row.isChecked)
            assertNull("The row already has a check icon", row.checkedIcon)
        }
        if (row is ViewGroup) {
            for (index in 0 until row.childCount) {
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, row.getChildAt(index).importantForAccessibility)
            }
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(200)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.externalCacheDir, "selection-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun preview(
        fontScale: Float = 1f,
        dark: Boolean = false,
        colorSeed: Int? = null,
        block: (ActivityScenario<SettingsPreviewActivity>) -> Unit
    ) {
        SettingsPreviewActivity.appearance = SettingsPreviewActivity.Appearance(fontScale, dark)
        val intent = Intent(ApplicationProvider.getApplicationContext(), SettingsPreviewActivity::class.java).apply {
            putExtra(SettingsPreviewActivity.EXTRA_WIDTH_DP, 320)
            colorSeed?.let { putExtra(SettingsPreviewActivity.EXTRA_COLOR_SEED, it) }
        }
        ActivityScenario.launch<SettingsPreviewActivity>(intent).use { scenario ->
            scenario.onActivity { it.findViewById<View>(R.id.settings_scroll).visibility = View.VISIBLE }
            block(scenario)
        }
    }

    private fun friend() = UserSummary(
        isu = 900001,
        name = "Александрова-Константинопольская Александра Александровна",
        pictureUrl = null,
        groups = listOf(UserGroup("P3100", 1, "Факультет")),
        sharing = UserSharing(sport = true, schedule = true)
    )

    private companion object {
        const val LONG_SPORT = "Современные танцы и общая физическая подготовка"
    }
}
