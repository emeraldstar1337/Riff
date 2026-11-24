package dev.lad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
internal fun ExpressiveSheet(onClose: () -> Unit, content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val animations = LocalLadSettings.current.animations
    val reveal = remember { Animatable(if (animations) 0f else 1f) }
    val drag = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            reveal.animateTo(0f, LadMotion.fastEffects(animations))
            onClose()
        }
    }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, LadMotion.defaultSpatial(animations))
    }
    Dialog(
        onDismissRequest = ::close,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)
                ?.window
                ?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = .36f))
                    .clickable(onClick = ::close)
            )
            BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    Modifier.widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * .94f)
                        .graphicsLayer {
                            translationY = (1f - reveal.value) * size.height + drag.value
                            alpha = reveal.value.coerceIn(0f, 1f)
                        },
                    shape =
                        RoundedCornerShape(
                            topStart = 32.dp,
                            topEnd = 32.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp,
                        ),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column {
                        Box(
                            Modifier.fillMaxWidth()
                                .height(32.dp)
                                .draggable(
                                    rememberDraggableState { amount ->
                                        scope.launch {
                                            drag.snapTo((drag.value + amount).coerceAtLeast(0f))
                                        }
                                    },
                                    Orientation.Vertical,
                                    onDragStopped = { velocity ->
                                        if (
                                            drag.value > with(density) { 80.dp.toPx() } ||
                                                velocity > 1400
                                        )
                                            close()
                                        else
                                            scope.launch {
                                                drag.animateTo(
                                                    0f,
                                                    LadMotion.defaultSpatial(animations),
                                                )
                                            }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(36.dp, 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = .4f
                                        ),
                                        RoundedCornerShape(4.dp),
                                    )
                            )
                        }
                        content()
                    }
                }
            }
        }
    }
}
