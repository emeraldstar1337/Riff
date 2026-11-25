@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.lad.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lad.R
import dev.lad.data.Settings
import dev.lad.domain.LyricLine
import dev.lad.i18n.rememberText

@Composable
fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Surface(shape = LadShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(
                    checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onChange,
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (description.isNotBlank())
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Switch(
                checked,
                null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        if (checked) Icons.Default.Check else Icons.Default.Close,
                        null,
                        Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Composable
fun ChoiceChips(
    current: String,
    values: List<Pair<String, String>>,
    swatches: Map<String, Color> = emptyMap(),
    onSelect: (String) -> Unit,
) {
    if (values.isEmpty()) return
    // M3 Expressive connected button group: always fits evenly in a single non-scrolling row
    BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
        val count = values.size
        val totalChars = values.sumOf { it.second.length } + swatches.size * 2
        val compact = count >= 4 || maxWidth < 330.dp || totalChars > 26
        val dense = count >= 5 || totalChars > 35 || maxWidth < 300.dp
        val horizontalPad =
            when {
                dense -> 4.dp
                compact -> 6.dp
                else -> 12.dp
            }
        val textSize =
            when {
                dense && totalChars > 38 -> 10.5.sp
                dense -> 11.sp
                compact -> 12.sp
                else -> 13.5.sp
            }
        val swatchSize = if (dense) 8.dp else 10.dp
        val swatchGap = if (dense) 4.dp else 6.dp
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            values.forEachIndexed { index, (value, name) ->
                val checked = current == value
                val swatch = swatches[value]
                val itemWeight =
                    if (count <= 3 && totalChars <= 26 && swatches.isEmpty()) 1f
                    else
                        4.5f +
                            name.length.coerceIn(3, 14) * 0.55f +
                            (if (swatch != null) 1.2f else 0f)
                ToggleButton(
                    checked = checked,
                    contentPadding = PaddingValues(horizontal = horizontalPad, vertical = 10.dp),
                    onCheckedChange = { onSelect(value) },
                    modifier =
                        Modifier.weight(itemWeight)
                            .defaultMinSize(minWidth = 1.dp, minHeight = 48.dp)
                            .heightIn(min = 48.dp)
                            .semantics {
                                role = Role.RadioButton
                                selected = checked
                            },
                    shapes =
                        when {
                            values.size == 1 -> ToggleButtonDefaults.shapes()
                            index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            index == values.lastIndex ->
                                ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                ) {
                    if (swatch != null) {
                        Box(Modifier.size(swatchSize).clip(CircleShape).background(swatch))
                        Spacer(Modifier.width(swatchGap))
                    }
                    Text(
                        name,
                        fontSize = textSize,
                        lineHeight = (textSize.value + 3f).sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CoverShapeChoices(
    current: String,
    values: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    if (values.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEachIndexed { index, (value, name) ->
            val checked = current == value
            ToggleButton(
                checked = checked,
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 10.dp),
                onCheckedChange = { onSelect(value) },
                modifier =
                    Modifier.weight(1f)
                        .defaultMinSize(minWidth = 1.dp, minHeight = 68.dp)
                        .semantics {
                            role = Role.RadioButton
                            selected = checked
                        },
                shapes =
                    when {
                        values.size == 1 -> ToggleButtonDefaults.shapes()
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == values.lastIndex ->
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.size(24.dp)
                            .clip(coverClipShape(value, 6))
                            .background(
                                LocalContentColor.current.copy(
                                    alpha = if (checked) 0.94f else 0.62f
                                )
                            )
                    )
                    Text(
                        name,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlayPauseMorphPreview(
    pauseShape: String,
    playShape: String,
    animations: Boolean,
) {
    val strings = rememberText()
    var previewPlaying by remember { mutableStateOf(true) }
    val morphFraction by
        animateFloatAsState(
            if (previewPlaying) 1f else 0f,
            if (animations) spring(dampingRatio = 0.74f, stiffness = 260f) else snap(),
            label = "preview morph",
        )
    val previewScale by
        animateFloatAsState(
            if (previewPlaying) 1f else 0.90f,
            if (animations) spring(dampingRatio = 0.60f, stiffness = 280f) else snap(),
            label = "preview scale",
        )
    val previewTwist by
        animateFloatAsState(
            if (!previewPlaying && pauseShape != playShape) -6f else 0f,
            if (animations) spring(dampingRatio = 0.64f, stiffness = 240f) else snap(),
            label = "preview twist",
        )
    val buttonCorner by
        animateDpAsState(
            if (previewPlaying) 16.dp else 28.dp,
            LadMotion.fastSpatial(animations),
            label = "preview btn corner",
        )
    Surface(
        onClick = { previewPlaying = !previewPlaying },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(72.dp)
                    .graphicsLayer {
                        scaleX = previewScale
                        scaleY = previewScale
                        rotationZ = previewTwist
                    }
                    .clip(MorphedCoverShape(pauseShape, playShape, morphFraction))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Album,
                    null,
                    Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (previewPlaying) strings[R.string.now_playing] else strings[R.string.paused],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    strings[R.string.morph_preview_tap_hint],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(buttonCorner),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (previewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppearanceSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onSave: (Float) -> Unit,
) =
    SettingsSlider(
        title,
        rememberText().format(R.string.decimal_two, value),
        value,
        range,
        onSave = onSave,
    )

@Composable
internal fun SettingsSlider(
    title: String,
    summary: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
    onSave: (Float) -> Unit,
) {
    val strings = rememberText()
    var draft by remember(value) { mutableFloatStateOf(value.coerceIn(range)) }
    Surface(shape = LadShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (draft == value) summary else strings.format(R.string.decimal_two, draft),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Slider(
                draft,
                { draft = it },
                valueRange = range,
                steps = steps,
                enabled = enabled,
                onValueChangeFinished = { onSave(draft) },
            )
        }
    }
}

@Composable
internal fun SettingsAction(
    title: String,
    detail: String,
    icon: ImageVector,
    onClick: () -> Unit,
) = StyleChoice(title, detail, false, icon, onClick = onClick)

internal data class PlayerAction(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val run: () -> Unit,
)

@Composable
internal fun ExpressiveActions(
    title: String,
    detail: String,
    onClose: () -> Unit,
    actions: List<PlayerAction>,
) {
    ExpressiveSheet(onClose) {
        Column(
            Modifier.widthIn(max = 640.dp)
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (detail.isNotBlank())
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            Spacer(Modifier.height(8.dp))
            actions.forEach { action ->
                StyleChoice(action.title, action.detail, false, action.icon, onClick = action.run)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
internal fun StyleChoice(
    title: String,
    description: String,
    selected: Boolean,
    icon: ImageVector,
    shape: Shape? = null,
    onClick: () -> Unit,
) {
    val strings = rememberText()
    val animations = LocalLadSettings.current.animations
    val corner by
        animateDpAsState(
            if (selected) 28.dp else 20.dp,
            if (animations) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
            label = "choice corner",
        )
    val iconCorner by
        animateDpAsState(
            if (selected) 16.dp else 20.dp,
            if (animations) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
            label = "choice icon corner",
        )
    val container by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
            if (animations) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
            label = "choice container",
        )
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = shape ?: RoundedCornerShape(corner),
        color = container,
        contentColor = contentColor,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(iconCorner),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    icon,
                    null,
                    Modifier.padding(12.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (description.isNotBlank())
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (selected)
                    Icon(
                        Icons.Default.CheckCircle,
                        strings[R.string.selection_label],
                        tint = MaterialTheme.colorScheme.primary,
                    )
                else if (shape != null)
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
    }
}

internal fun segmentedItemShape(index: Int, count: Int): Shape =
    when {
        count <= 1 -> RoundedCornerShape(28.dp)
        index == 0 ->
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        index == count - 1 ->
            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        else -> RoundedCornerShape(8.dp)
    }

@Composable
internal fun SettingsGroupLabel(title: String) {
    Text(
        title,
        Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun SettingsNote(title: String, text: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MiniStylePreview(settings: Settings) {
    val strings = rememberText()
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                strings[R.string.preview],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (settings.miniStyle != "slim")
                            Artwork(null, strings[R.string.lad], Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings[R.string.your_favourite_track],
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                if (settings.miniStyle == "expanded") "1:24 / 3:42"
                                else strings[R.string.artist],
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Icon(
                                Icons.Default.Pause,
                                null,
                                Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Icon(Icons.Default.SkipNext, null, Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { 0.38f },
                        Modifier.fillMaxWidth().height(3.dp),
                    )
                }
            }
            Text(
                strings[R.string.the_preview_works_even_when_no],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun NavigationPreview(selected: List<String>, names: Map<String, String>) {
    val strings = rememberText()
    val icons =
        mapOf(
            "home" to Icons.Default.Home,
            "library" to Icons.Default.LibraryMusic,
            "playlists" to Icons.AutoMirrored.Filled.QueueMusic,
            "stats" to Icons.Default.Insights,
        )
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                strings[R.string.your_navigation],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    selected.forEachIndexed { index, key ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color =
                                if (index == 0) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                        ) {
                            Icon(
                                icons[key] ?: Icons.Default.Home,
                                names[key],
                                Modifier.padding(12.dp).size(24.dp),
                            )
                        }
                    }
                }
            }
            Text(
                selected.joinToString(" → ") { names[it] ?: it },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun LyricsStylePreview(settings: Settings) {
    val strings = rememberText()
    CompositionLocalProvider(LocalLadSettings provides settings.copy(animations = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement =
                    Arrangement.spacedBy(
                        ((settings.lyricsSpacing.coerceIn(1.1f, 1.7f) - 1f) * 28f)
                            .coerceAtLeast(4f)
                            .dp
                    ),
            ) {
                Text(
                    strings[R.string.lyrics_preview],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                LyricLineText(LyricLine(0, strings[R.string.the_music_stays_with_you]), true, 0, {})
                LyricLineText(
                    LyricLine(1000, strings[R.string.every_line_follows_your_rhythm]),
                    false,
                    0,
                    {},
                )
            }
        }
    }
}

@Composable
internal fun GlowStylePreview(settings: Settings, seed: Color? = null) {
    val strings = rememberText()
    CompositionLocalProvider(LocalLadSettings provides settings.copy(animations = false)) {
        Surface(shape = RoundedCornerShape(32.dp)) {
            AmbientBackdrop(
                seed ?: MaterialTheme.colorScheme.primary,
                enabled = settings.ambientBackground,
                animations = false,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            ) {
                Column(
                    Modifier.align(Alignment.Center).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        strings[R.string.the_mood_of_your_music],
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
