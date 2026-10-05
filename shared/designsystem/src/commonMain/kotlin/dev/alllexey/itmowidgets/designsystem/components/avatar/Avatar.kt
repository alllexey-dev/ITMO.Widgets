package dev.alllexey.itmowidgets.designsystem.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The image loader [Avatar] uses. The host provides its own (the app's Coil setup, a preview's fake); without one the
 * platform's singleton loader is used. The kit itself never adds a network fetcher.
 */
val LocalAvatarImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

/**
 * A person's round avatar: their photo from [pictureUrl] when it loads, their initials until then and whenever it
 * fails or there is no URL, and an empty circle without a [name]. Each URL is its own request, so a late failure of an
 * earlier URL never replaces a newer photo (`core/ui/AvatarView.kt` guarded that by hand). Decorative unless
 * [contentDescription] is given, since the name usually stands beside it.
 */
@Composable
fun Avatar(
    name: String?,
    pictureUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = DefaultSize,
    contentDescription: String? = null,
) {
    val described = if (contentDescription == null) Modifier else Modifier.semantics(mergeDescendants = true) {
        this.contentDescription = contentDescription
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(ItmoTheme.colorScheme.surfaceVariant)
            .then(described),
        contentAlignment = Alignment.Center,
    ) {
        if (name == null) return@Box
        val url = pictureUrl?.takeIf { it.isNotBlank() }
        if (url == null) {
            Initials(name)
        } else {
            key(url) { Photo(url, name) }
        }
    }
}

@Composable
private fun Photo(url: String, name: String) {
    val loader = LocalAvatarImageLoader.current ?: SingletonImageLoader.get(LocalPlatformContext.current)
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        imageLoader = loader,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    ) {
        val state by painter.state.collectAsState()
        if (state is AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent()
        } else {
            Initials(name)
        }
    }
}

@Composable
private fun Initials(name: String) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 40 % of the circle in pixels, unscaled by the font scale, as AvatarView sized its text.
        val fontSize = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * INITIALS_RATIO).toSp() }
        Text(
            initialsOf(name),
            color = ItmoTheme.colorScheme.onSurface,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            // TalkBack reads the name or [Avatar]'s description, never the letters.
            modifier = Modifier.semantics { hideFromAccessibility() },
        )
    }
}

/** The first letters of the first and last words, upper case; one letter for one word, `?` for none. */
internal fun initialsOf(name: String): String {
    val parts = name.trim().split(Whitespace).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

private val Whitespace = Regex("\\s+")

/** `item_user_row.xml`'s avatar. */
private val DefaultSize = 48.dp

private const val INITIALS_RATIO = 0.4f
