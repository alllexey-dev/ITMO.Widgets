package dev.alllexey.itmowidgets.core.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.card.MaterialCardView

/** Exposes a whole-row choice once, including its visual selection and availability. */
fun View.bindSelectionAccessibility(
    label: CharSequence,
    selected: Boolean,
    selectable: Boolean = true,
    multiple: Boolean = false
) {
    isSelected = selectable && selected
    if (this is MaterialCardView) {
        // MaterialCardView writes native checkable/checked state after the delegate.
        // Its checked setter ignores disabled cards, including a recycled private row.
        // Rebind that state before restoring availability; the row owns its check icon.
        checkedIcon = null
        isEnabled = true
        isCheckable = true
        isChecked = isSelected
        isCheckable = selectable
        isEnabled = selectable
    }
    contentDescription = label
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    ViewCompat.setScreenReaderFocusable(this, true)
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    }
    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = when {
                host is MaterialCardView -> "androidx.cardview.widget.CardView"
                !selectable -> "android.view.View"
                multiple -> "android.widget.CheckBox"
                else -> "android.widget.RadioButton"
            }
            info.isCheckable = selectable
            info.isChecked = host.isSelected
            info.isSelected = host.isSelected
        }
    })
}
