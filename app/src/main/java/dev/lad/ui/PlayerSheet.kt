package dev.lad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.lad.R
import dev.lad.i18n.rememberText
import kotlinx.coroutines.launch

@Composable
internal fun PlayerSheet(
    title: String,
    subtitle: String,
    tag: String,
    onClose: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val strings = rememberText()
    val animations = LocalLadSettings.current.animations
    val reveal = remember { Animatable(if (animations) 0f else 1f) }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (!closing) {
            closing = true
            scope.launch {
                reveal.animateTo(0f, tween(if (animations) 160 else 0))
                onClose()
            }
        }
    }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(if (animations) 220 else 0)) }
    Dialog(
        onDismissRequest = ::close,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)
                ?.window
                ?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                    .clickable(onClick = ::close)
            )
            Box(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    Modifier.widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(0.96f)
                        .graphicsLayer {
                            translationY = (1f - reveal.value) * size.height + offset.value
                        }
                        .testTag("$tag-sheet"),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxWidth()
                                .height(36.dp)
                                .testTag("$tag-handle")
                                .draggable(
                                    rememberDraggableState { delta ->
                                        scope.launch {
                                            offset.snapTo((offset.value + delta).coerceAtLeast(0f))
                                        }
                                    },
                                    Orientation.Vertical,
                                    onDragStopped = { velocity ->
                                        if (
                                            offset.value > with(density) { 96.dp.toPx() } ||
                                                velocity > 1400
                                        )
                                            close()
                                        else offset.animateTo(0f, tween(if (animations) 180 else 0))
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(36.dp, 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.4f
                                        ),
                                        RoundedCornerShape(4.dp),
                                    )
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, bottom = 12.dp)
                                .testTag("$tag-header"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(title, style = MaterialTheme.typography.headlineSmall)
                                if (subtitle.isNotBlank())
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                            }
                            IconButton(::close, Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    strings.format(R.string.player_sheet_close, title),
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        CompositionLocalProvider(LocalOverscrollFactory provides null) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .weight(1f)
                                    .clipToBounds()
                                    .testTag("$tag-body"),
                                content = content,
                            )
                        }
                    }
                }
            }
        }
    }
}
