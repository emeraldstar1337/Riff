package dev.lad.ui

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest

internal data class ReadyArtwork(
    val uri: String?,
    val drawable: Drawable?,
    val bitmap: ImageBitmap? = (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap(),
    val sequence: Int = 0,
)

internal val LocalReadyArtwork = staticCompositionLocalOf<ReadyArtwork?> { null }

@Composable
internal fun ArtworkMotion(
    uri: String?,
    modifier: Modifier = Modifier,
    direction: Int = 1,
    imageLoader: ImageLoader = LocalContext.current.imageLoader,
    content: @Composable (String?) -> Unit,
) {
    val settings = LocalLadSettings.current
    val context = LocalContext.current
    val width = LocalConfiguration.current.screenWidthDp.dp
    val edge = with(LocalDensity.current) { width.roundToPx() }.coerceIn(256, 1024)
    var ready by remember { mutableStateOf<ReadyArtwork?>(null) }
    LaunchedEffect(uri, imageLoader, edge) {
        val result =
            if (uri.isNullOrBlank()) null
            else
                imageLoader
                    .execute(
                        ImageRequest.Builder(context)
                            .data(uri)
                            .size(edge)
                            .allowHardware(false)
                            .build()
                    )
                    .drawable
        val imageBitmap = (result as? BitmapDrawable)?.bitmap?.asImageBitmap()
        ready = ReadyArtwork(uri, result, imageBitmap, (ready?.sequence ?: 0) + 1)
    }
    val image = ready
    if (image == null) {
        Box(modifier) { content(null) }
        return
    }
    AnimatedContent(
        targetState = image,
        contentKey = { it.uri },
        modifier = modifier,
        transitionSpec = {
            val animated = settings.animations
            val slideSpec =
                if (animated) spring<IntOffset>(dampingRatio = 0.90f, stiffness = 340f) else snap()
            val scaleSpec =
                if (animated) spring<Float>(dampingRatio = 0.90f, stiffness = 340f) else snap()
            val fadeSpec =
                if (animated) tween<Float>(300, easing = FastOutSlowInEasing) else snap()
            val sign = if (direction < 0) -1 else 1
            when (settings.coverTransition) {
                    "cards" ->
                        (slideInHorizontally(slideSpec) { it * sign } +
                                scaleIn(scaleSpec, initialScale = .90f))
                            .togetherWith(
                                slideOutHorizontally(slideSpec) { -it * sign } +
                                    scaleOut(scaleSpec, targetScale = .90f)
                            )
                    "zoom" ->
                        (fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = .92f))
                            .togetherWith(
                                fadeOut(fadeSpec) + scaleOut(scaleSpec, targetScale = 1.06f)
                            )
                    "morph" ->
                        (fadeIn(fadeSpec) +
                                scaleIn(scaleSpec, initialScale = .84f) +
                                slideInHorizontally(slideSpec) { it * sign / 5 })
                            .togetherWith(
                                fadeOut(fadeSpec) +
                                    scaleOut(scaleSpec, targetScale = .84f) +
                                    slideOutHorizontally(slideSpec) { -it * sign / 5 }
                            )
                    else ->
                        (fadeIn(fadeSpec) + slideInHorizontally(slideSpec) { it * sign / 12 })
                            .togetherWith(ExitTransition.None)
                }
                .apply { targetContentZIndex = targetState.sequence.toFloat() }
                .using(SizeTransform(clip = false))
        },
        label = "artwork transition",
    ) { current ->
        CompositionLocalProvider(LocalReadyArtwork provides current) {
            Box(Modifier.fillMaxSize()) { content(current.uri) }
        }
    }
}
