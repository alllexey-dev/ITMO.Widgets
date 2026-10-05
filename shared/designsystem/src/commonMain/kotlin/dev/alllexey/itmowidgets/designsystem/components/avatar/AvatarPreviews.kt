package dev.alllexey.itmowidgets.designsystem.components.avatar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.test.FakeImageLoaderEngine
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import kotlinx.coroutines.Dispatchers

/** Initials at two sizes, a one-word name, and the empty circle of a missing user. */
@Preview
@Composable
private fun AvatarInitialsPreview() = ItmoPreview {
    AvatarRow {
        Avatar(PreviewFixtures.LongPersonName, pictureUrl = null, size = 96.dp)
        Avatar(PreviewFixtures.ShortPersonName, pictureUrl = null)
        Avatar("александра", pictureUrl = null)
        Avatar(name = null, pictureUrl = null)
    }
}

/** A photo that loads, and one that fails and keeps the initials. */
@Preview
@Composable
private fun AvatarPhotoPreview() = ItmoPreview {
    val context = LocalPlatformContext.current
    val loader = remember(context) {
        val engine = FakeImageLoaderEngine.Builder()
            .intercept(PHOTO_URL, ColorImage(PHOTO_COLOR))
            .build()
        // Unconfined: the fake answers within the first frame, so the capture shows the result, not the loading.
        ImageLoader.Builder(context).components { add(engine) }.coroutineContext(Dispatchers.Unconfined).build()
    }
    CompositionLocalProvider(LocalAvatarImageLoader provides loader) {
        AvatarRow {
            Avatar(PreviewFixtures.LongPersonName, PHOTO_URL, size = 96.dp)
            Avatar(PreviewFixtures.ShortPersonName, PHOTO_URL)
            Avatar(PreviewFixtures.ShortPersonName, MISSING_URL)
        }
    }
}

@Composable
private fun AvatarRow(content: @Composable () -> Unit) {
    Row(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

private const val PHOTO_URL = "https://example.invalid/avatar.jpg"

/** No fake and no network fetcher in the kit: the request fails. */
private const val MISSING_URL = "https://example.invalid/missing.jpg"

private const val PHOTO_COLOR = 0xFF5B7FA6.toInt()
