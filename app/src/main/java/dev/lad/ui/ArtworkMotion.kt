package dev.lad.ui

import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest

internal data class ReadyArtwork(val uri: String?, val drawable: Drawable?, val sequence: Int = 0)

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
    val edge = with(LocalDensity.current) { width.roundToPx() }.coerceIn(256, 2048)
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
        ready = ReadyArtwork(uri, result, (ready?.sequence ?: 0) + 1)
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
            val duration = if (settings.animations) 640 else 0
            val motion =
                tween<androidx.compose.ui.unit.IntOffset>(duration, easing = LadMotion.easing)
            val sign = if (direction < 0) -1 else 1
            when (settings.coverTransition) {
                    "cards" ->
                        (slideInHorizontally(motion) { it * sign } +
                                scaleIn(
                                    tween(duration, easing = LadMotion.easing),
                                    initialScale = .88f,
                                ))
                            .togetherWith(
                                slideOutHorizontally(motion) { -it * sign } +
                                    scaleOut(
                                        tween(duration, easing = LadMotion.easing),
                                        targetScale = .88f,
                                    )
                            )
                    "zoom" ->
                        (fadeIn(tween(duration, easing = LadMotion.easing)) +
                                scaleIn(
                                    tween(duration, easing = LadMotion.easing),
                                    initialScale = .94f,
                                ))
                            .togetherWith(
                                fadeOut(tween(duration, easing = LadMotion.easing)) +
                                    scaleOut(
                                        tween(duration, easing = LadMotion.easing),
                                        targetScale = 1.06f,
                                    )
                            )
                    "morph" ->
                        (fadeIn(tween(duration, easing = LadMotion.easing)) +
                                scaleIn(
                                    tween(duration, easing = LadMotion.easing),
                                    initialScale = .82f,
                                ) +
                                slideInHorizontally(motion) { it * sign / 5 })
                            .togetherWith(
                                fadeOut(tween(duration, easing = LadMotion.easing)) +
                                    scaleOut(
                                        tween(duration, easing = LadMotion.easing),
                                        targetScale = .82f,
                                    ) +
                                    slideOutHorizontally(motion) { -it * sign / 5 }
                            )
                    else ->
                        (fadeIn(tween(duration, easing = LadMotion.easing)) +
                                slideInHorizontally(motion) { it * sign / 10 })
                            .togetherWith(ExitTransition.None)
                }
                .apply { targetContentZIndex = targetState.sequence.toFloat() }
                .using(SizeTransform(clip = false))
        },
        label = "artwork transition",
    ) { image ->
        CompositionLocalProvider(LocalReadyArtwork provides image) {
            Box(Modifier.fillMaxSize()) { content(image.uri) }
        }
    }
}
