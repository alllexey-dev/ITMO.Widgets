package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.feature.recordbook.ui.SheetScoresIosRoute
import platform.UIKit.UIViewController

/**
 * `Мои баллы` (`AppRoutes.SheetScores`) inside a SwiftUI sheet, as Android's `SheetScoresEntry`: it closes through
 * [onClose] once the total is saved, and a failed save goes to [saveFailed] as its text for Swift to show.
 */
fun sheetScoresViewController(
    scores: SheetScoresArgs,
    onClose: () -> Unit,
    saveFailed: (String) -> Unit,
): UIViewController = recordbookSheetController {
    SheetScoresIosRoute(scores, onClose, saveFailed)
}

/**
 * A sheet's content in a see-through controller: the SwiftUI sheet's grouped background shows around the
 * `SheetScaffold`, which draws the same colour. Only the sides and the home indicator are kept clear: the sheet starts
 * below the status bar, whose height Compose still reports.
 */
private fun recordbookSheetController(content: @Composable () -> Unit): UIViewController =
    screenController(opaque = false) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
        ) {
            content()
        }
    }
