@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lad.BuildConfig
import dev.lad.R
import dev.lad.data.AppearancePresets
import dev.lad.data.Settings
import dev.lad.domain.BitPerfectState
import dev.lad.domain.FolderRules
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import dev.lad.i18n.rememberText
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private enum class SettingsPage(
    val title: Int,
    val detail: Int,
    val icon: ImageVector,
) {
    THEME(
        R.string.theme_and_colours,
        R.string.light_dark_monet_and_custom_palettes,
        Icons.Default.Palette,
    ),
    SHAPES(
        R.string.shapes_and_motion,
        R.string.artwork_shapes_and_transitions,
        Icons.Default.Category,
    ),
    PLAYER(
        R.string.player,
        R.string.layout_visualizer_and_track_transitions,
        Icons.Default.MusicNote,
    ),
    MINI(R.string.mini_player, R.string.size_and_playback_controls, Icons.Default.PlayCircle),
    NAVIGATION(R.string.navigation, R.string.tabs_and_their_order, Icons.Default.Dashboard),
    AUDIO(
        R.string.sound_and_devices,
        R.string.equalizer_balance_and_headphone_profiles,
        Icons.Default.Headphones,
    ),
    LIBRARY(
        R.string.settings_screen_library,
        R.string.folders_duration_duplicates_and_hidden_files,
        Icons.Default.Folder,
    ),
    HISTORY(
        R.string.history_and_recaps,
        R.string.statistics_and_listening_recaps,
        Icons.Default.Insights,
    ),
    LANGUAGE(
        R.string.language,
        R.string.russian_english_or_device_language,
        Icons.Default.Language,
    ),
    ABOUT(R.string.about_lad, R.string.version_and_features, Icons.Default.Info),
}

@Composable
fun SettingsScreen(
    settings: Settings,
    vm: LadViewModel,
    onDemo: (Boolean) -> Unit,
    onDuplicates: () -> Unit = {},
) {
    val strings = rememberText()
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var included by remember { mutableStateOf(false) }
    var excluded by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    val folders by vm.folders.collectAsStateWithLifecycle()
    val bitPerfectFlow =
        remember(vm) { vm.playback.state.map { it.bitPerfect }.distinctUntilChanged() }
    val bitPerfect by bitPerfectFlow.collectAsStateWithLifecycle(initialValue = BitPerfectState())
    val language by AppLanguage.selected.collectAsStateWithLifecycle()
    val cover =
        remember(vm) { vm.playback.state.map { it.current?.artwork }.distinctUntilChanged() }
    val artwork by cover.collectAsStateWithLifecycle(initialValue = null)
    val previewSeed = artworkSeed(artwork, settings.artworkColor)
    val list = remember(page) { LazyListState() }
    LaunchedEffect(page) {
        if (page == SettingsPage.LIBRARY) vm.loadFolders()
        if (page == SettingsPage.AUDIO) vm.playback.connect()
    }
    BackHandler(page != null && !included && !excluded && !clear) { page = null }

    LazyColumn(
        state = list,
        contentPadding = PaddingValues(LadSpacing.page),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val activePage = page
        if (activePage == null) {
            item {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    placeholder = { Text(strings[R.string.find_a_setting]) },
                    shape = LadShapes.hero,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                )
            }
            SettingsPage.entries
                .filter {
                    query.isBlank() ||
                        "${strings[it.title]} ${strings[it.detail]}".contains(query, true)
                }
                .forEach { section ->
                    item(section.name) {
                        StyleChoice(
                            strings[section.title],
                            strings[section.detail],
                            false,
                            section.icon,
                        ) {
                            page = section
                        }
                    }
                }
        } else {
            item {
                TextButton(
                    { page = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Default.ChevronLeft, null)
                    Text(strings[R.string.all_settings])
                }
            }
            item { BitsTitle(strings[activePage.title]) }
            when (activePage) {
                SettingsPage.THEME -> themeSettingsSection(settings, strings, vm::updateSettings)
                SettingsPage.SHAPES ->
                    shapesSettingsSection(settings, previewSeed, strings, vm::updateSettings)
                SettingsPage.PLAYER -> playerSettingsSection(settings, strings, vm::updateSettings)
                SettingsPage.MINI -> miniPlayerSettingsSection(settings, strings, vm::updateSettings)
                SettingsPage.NAVIGATION ->
                    navigationSettingsSection(settings, strings, vm::updateSettings)
                SettingsPage.AUDIO ->
                    audioSettingsSection(
                        settings = settings,
                        bitPerfect = bitPerfect,
                        strings = strings,
                        onUpdate = vm::updateSettings,
                        onOpenAudioPanel = { vm.audioOpen.value = true },
                    )
                SettingsPage.LIBRARY ->
                    librarySettingsSection(
                        settings = settings,
                        strings = strings,
                        onUpdate = vm::updateSettings,
                        onRefresh = vm::refresh,
                        onIncluded = { included = true },
                        onExcluded = { excluded = true },
                        onDuplicates = onDuplicates,
                    )
                SettingsPage.HISTORY ->
                    historySettingsSection(
                        settings = settings,
                        strings = strings,
                        onUpdate = vm::updateSettings,
                        onDemo = onDemo,
                        onClear = { clear = true },
                    )
                SettingsPage.LANGUAGE ->
                    languageSettingsSection(
                        settings = settings,
                        language = language,
                        strings = strings,
                        onLanguage = vm::setLanguage,
                        onUpdate = vm::updateSettings,
                    )
                SettingsPage.ABOUT -> aboutSettingsSection(strings)
            }
        }
    }
    if (included)
        FolderPicker(true, settings.includedFolders, folders, { included = false }) { paths ->
            vm.updateSettings { it.copy(includedFolders = paths) }
            included = false
        }
    if (excluded)
        FolderPicker(false, settings.excludedFolders, folders, { excluded = false }) { paths ->
            vm.updateSettings { it.copy(excludedFolders = paths) }
            excluded = false
        }
    if (clear)
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text(strings[R.string.settings_screen_clear_history]) },
            text = { Text(strings[R.string.history_and_statistics_will_be_deleted]) },
            confirmButton = {
                TextButton(
                    {
                        vm.clearStatistics()
                        clear = false
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.clear])
                }
            },
            dismissButton = {
                TextButton(
                    { clear = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.cancel])
                }
            },
        )
}

private fun LazyListScope.themeSettingsSection(
    settings: Settings,
    strings: TextResources,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    item {
        SectionTitle(
            strings[R.string.appearance],
            strings[R.string.theme_and_accent_colour],
        )
    }
    item {
        ChoiceChips(
            settings.theme,
            listOf(
                "system" to strings[R.string.system],
                "light" to strings[R.string.light],
                "dark" to strings[R.string.dark],
            ),
        ) {
            onUpdate { current -> current.copy(theme = it) }
        }
    }
    item {
        SettingSwitch(
            "Monet",
            strings[R.string.colours_from_your_wallpaper],
            settings.dynamicColor,
        ) {
            onUpdate { current -> current.copy(dynamicColor = it) }
        }
    }
    item {
        ChoiceChips(
            settings.accent,
            listOf(
                "violet" to strings[R.string.iris],
                "ocean" to strings[R.string.ocean],
                "mint" to strings[R.string.mint],
                "rose" to strings[R.string.rose],
                "amber" to strings[R.string.amber],
            ),
        ) {
            onUpdate { current -> current.copy(accent = it, dynamicColor = false) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.black_background],
            strings[R.string.deep_black_in_the_dark_theme],
            settings.pureBlack,
        ) {
            onUpdate { current -> current.copy(pureBlack = it) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.artwork_colours],
            strings[R.string.player_colours_follow_the_music],
            settings.artworkColor,
        ) {
            onUpdate { current -> current.copy(artworkColor = it) }
        }
    }
    item {
        SectionTitle(
            strings[R.string.recaps],
            strings[R.string.statistics_card_appearance],
        )
    }
    item {
        SettingSwitch(
            strings[R.string.artwork_in_recaps],
            strings[R.string.recap_colours_follow_the_top_track],
            settings.recapArtwork,
        ) {
            onUpdate { current -> current.copy(recapArtwork = it) }
        }
    }
}

private fun LazyListScope.shapesSettingsSection(
    settings: Settings,
    previewSeed: Color?,
    strings: TextResources,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    item {
        SectionTitle(
            strings[R.string.glow],
            strings[R.string.the_main_area_of_the_artwork],
        )
    }
    item { GlowStylePreview(settings, previewSeed) }
    item {
        ChoiceChips(
            settings.glowPreset,
            AppearancePresets.glow.map { it.id to strings.label(it.name) },
        ) { id ->
            onUpdate { current ->
                AppearancePresets.glow.firstOrNull { it.id == id }?.apply(current) ?: current
            }
        }
    }
    item {
        AppearanceSlider(strings[R.string.glow_intensity], settings.glowStrength, 0f..1.8f) {
            onUpdate { current -> current.copy(glowPreset = "custom", glowStrength = it) }
        }
    }
    item {
        AppearanceSlider(strings[R.string.glow_radius], settings.glowRadius, 0.5f..1.6f) {
            onUpdate { current -> current.copy(glowPreset = "custom", glowRadius = it) }
        }
    }
    item {
        AppearanceSlider(strings[R.string.saturation], settings.glowSaturation, 0f..1f) {
            onUpdate { current -> current.copy(glowPreset = "custom", glowSaturation = it) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.animated_glow],
            strings[R.string.moves_only_while_music_is_playing],
            settings.glowMotion,
        ) {
            onUpdate { current -> current.copy(glowMotion = it) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.abstract_shapes],
            strings[R.string.three_soft_background_shapes_they_move],
            settings.glowShapes,
        ) {
            onUpdate { current -> current.copy(glowShapes = it) }
        }
    }
    item { SectionTitle(strings[R.string.shape_type]) }
    item {
        ChoiceChips(
            settings.shapeType,
            listOf(
                "mixed" to strings[R.string.mixed_shapes],
                "flower" to strings[R.string.flower],
                "pebble" to strings[R.string.pebble_shape],
                "circle" to strings[R.string.circle],
            ),
        ) { value ->
            onUpdate { it.copy(shapeType = value) }
        }
    }
    item {
        AppearanceSlider(strings[R.string.shape_scale], settings.shapeScale, 0.5f..1.6f) { value ->
            onUpdate { it.copy(shapeScale = value) }
        }
    }
    item {
        AppearanceSlider(strings[R.string.shape_opacity], settings.shapeOpacity, 0.02f..0.24f) {
            onUpdate { current -> current.copy(shapeOpacity = it) }
        }
    }
    item {
        AppearanceSlider(strings[R.string.motion_speed], settings.motionSpeed, 0.3f..1.5f) {
            onUpdate { current -> current.copy(motionSpeed = it) }
        }
    }
    item {
        SectionTitle(
            strings[R.string.artwork_title],
            strings[R.string.image_shapes_in_the_library_and],
        )
    }
    item {
        ChoiceChips(
            settings.coverShape,
            listOf(
                "rounded" to strings[R.string.rounded],
                "circle" to strings[R.string.circle],
                "flower" to strings[R.string.flower],
            ),
        ) {
            onUpdate { current -> current.copy(coverShape = it) }
        }
    }
    item { SectionTitle(strings[R.string.transitions], strings[R.string.interface_motion]) }
    item { SectionTitle(strings[R.string.cover_transition]) }
    item {
        ChoiceChips(
            settings.coverTransition,
            listOf(
                "fade" to strings[R.string.fade_transition],
                "zoom" to strings[R.string.zoom_transition],
                "cards" to strings[R.string.cards_transition],
                "morph" to strings[R.string.morph_transition],
            ),
        ) { value ->
            onUpdate { it.copy(coverTransition = value) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.motion],
            strings[R.string.smooth_transitions_and_artwork_changes],
            settings.animations,
        ) {
            onUpdate { current -> current.copy(animations = it) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.album_atmosphere],
            strings[R.string.a_gentle_glow_in_the_artwork],
            settings.ambientBackground,
        ) {
            onUpdate { current -> current.copy(ambientBackground = it) }
        }
    }
}

private fun LazyListScope.playerSettingsSection(
    settings: Settings,
    strings: TextResources,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    item {
        SectionTitle(
            strings[R.string.seek_bar],
            strings[R.string.choose_the_track_and_handle_shapes],
        )
    }
    item {
        SeekStyleChoices(settings.seekStyle) {
            onUpdate { current -> current.copy(seekStyle = it) }
        }
    }
    item {
        SectionTitle(
            strings[R.string.lyrics_appearance],
            strings[R.string.applies_to_synced_and_plain_lyrics],
        )
    }
    item { LyricsStylePreview(settings) }
    item {
        ChoiceChips(
            settings.lyricsPreset,
            AppearancePresets.lyrics.map { it.id to strings.label(it.name) },
        ) { id ->
            onUpdate { current ->
                AppearancePresets.lyrics.firstOrNull { it.id == id }?.apply(current) ?: current
            }
        }
    }
    item {
        AppearanceSlider(strings[R.string.text_size], settings.lyricsSize, 16f..34f) {
            onUpdate { current -> current.copy(lyricsPreset = "custom", lyricsSize = it) }
        }
    }
    item {
        AppearanceSlider(strings[R.string.line_spacing], settings.lyricsSpacing, 1.1f..1.7f) {
            onUpdate { current -> current.copy(lyricsPreset = "custom", lyricsSpacing = it) }
        }
    }
    item {
        AppearanceSlider(
            strings[R.string.inactive_line_brightness],
            settings.lyricsDim,
            0.2f..0.6f,
        ) {
            onUpdate { current -> current.copy(lyricsPreset = "custom", lyricsDim = it) }
        }
    }
    item {
        ChoiceChips(
            settings.lyricsAlignment,
            listOf(
                "start" to strings[R.string.left_aligned],
                "center" to strings[R.string.centred],
            ),
        ) {
            onUpdate { current -> current.copy(lyricsPreset = "custom", lyricsAlignment = it) }
        }
    }
    item {
        SectionTitle(
            strings[R.string.player],
            strings[R.string.layout_visualizer_and_track_transitions],
        )
    }
    item {
        ChoiceChips(
            settings.playerStyle,
            listOf(
                "immersive" to strings[R.string.large_artwork],
                "classic" to strings[R.string.compact],
                "minimal" to strings[R.string.minimal],
            ),
        ) {
            onUpdate { current -> current.copy(playerStyle = it) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.vinyl_artwork],
            strings[R.string.rotates_during_playback],
            settings.vinyl,
        ) {
            onUpdate { current -> current.copy(vinyl = it) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.visualizer],
            strings[R.string.the_wave_responds_to_the_audio],
            settings.visualizer,
        ) {
            onUpdate { current -> current.copy(visualizer = it) }
        }
    }
    item {
        SectionTitle(
            strings[R.string.track_transitions],
            strings[R.string.extra_mixing_is_off_by_default],
        )
    }
    item {
        SettingsSlider(
            strings[R.string.crossfade],
            if (settings.crossfadeSeconds == 0) strings[R.string.off]
            else strings.format(R.string.s, settings.crossfadeSeconds),
            settings.crossfadeSeconds.toFloat(),
            0f..12f,
            11,
            enabled = !settings.bitPerfect,
        ) {
            onUpdate { current -> current.copy(crossfadeSeconds = it.toInt()) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.pause_on_disconnect],
            strings[R.string.when_an_audio_device_disconnects],
            settings.pauseOnDisconnect,
        ) {
            onUpdate { current -> current.copy(pauseOnDisconnect = it) }
        }
    }
}

private fun LazyListScope.miniPlayerSettingsSection(
    settings: Settings,
    strings: TextResources,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    item {
        SettingsNote(
            strings[R.string.widget_settings_title],
            strings[R.string.widget_settings_hint],
        )
    }
    item { WidgetAddButtons() }
    item { MiniStylePreview(settings) }
    listOf(
            Triple("slim", R.string.slim, R.string.a_compact_row_with_music_and),
            Triple("card", R.string.card, R.string.artwork_title_and_a_large_pause),
            Triple("expanded", R.string.with_time, R.string.a_card_with_playback_time_and),
        )
        .forEach { (id, title, detail) ->
            item {
                StyleChoice(
                    strings[title],
                    strings[detail],
                    settings.miniStyle == id,
                    Icons.Default.PlayCircle,
                ) {
                    onUpdate { it.copy(miniStyle = id) }
                }
            }
        }
}

private fun LazyListScope.navigationSettingsSection(
    settings: Settings,
    strings: TextResources,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    val names =
        mapOf(
            "home" to strings[R.string.home],
            "library" to strings[R.string.music],
            "playlists" to strings[R.string.playlists],
            "stats" to strings[R.string.history],
        )
    val selected = settings.navigationTabs.split(',').filter { it in names }.distinct()
    item { NavigationPreview(selected, names) }
    item {
        SettingsNote(
            strings[R.string.your_order],
            strings[R.string.enable_the_sections_you_need_and],
        )
    }
    (selected + names.keys.filter { it !in selected }).forEach { key ->
        item {
            Surface(shape = LadShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        key in selected,
                        { checked ->
                            onUpdate { current ->
                                val before = current.navigationTabs.split(',').distinct()
                                val next =
                                    if (checked) (before + key).distinct() else before - key
                                current.copy(
                                    navigationTabs = next.ifEmpty { before }.joinToString(",")
                                )
                            }
                        },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(names[key] ?: key, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (key in selected)
                                strings.format(R.string.position, selected.indexOf(key) + 1)
                            else strings[R.string.hidden_from_navigation],
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (key in selected) {
                        IconButton(
                            {
                                onUpdate {
                                    it.copy(navigationTabs = moveTab(it.navigationTabs, key, -1))
                                }
                            },
                            enabled = selected.indexOf(key) > 0,
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, strings[R.string.move_up])
                        }
                        IconButton(
                            {
                                onUpdate {
                                    it.copy(navigationTabs = moveTab(it.navigationTabs, key, 1))
                                }
                            },
                            enabled = selected.indexOf(key) < selected.lastIndex,
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, strings[R.string.move_down])
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.audioSettingsSection(
    settings: Settings,
    bitPerfect: BitPerfectState,
    strings: TextResources,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onOpenAudioPanel: () -> Unit,
) {
    item {
        SettingSwitch(
            strings[R.string.bit_perfect_title],
            strings[R.string.bit_perfect_description],
            settings.bitPerfect,
        ) {
            onUpdate { current -> current.copy(bitPerfect = it) }
        }
    }
    item { BitPerfectInfo(bitPerfect) }
    if (settings.bitPerfect) {
        item {
            Text(
                strings[R.string.bit_perfect_effects_bypassed],
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    item {
        SectionTitle(
            strings[R.string.audio_profile],
            strings[R.string.settings_for_the_current_device],
        )
    }
    item {
        SettingsAction(
            strings[R.string.equalizer_and_device_profile],
            strings[R.string.tone_balance_and_volume],
            Icons.Default.Tune,
            onOpenAudioPanel,
        )
    }
    item { SectionTitle(strings[R.string.playback], strings[R.string.volume_and_tempo]) }
    item {
        SettingSwitch(
            "ReplayGain",
            strings[R.string.adjust_volume_using_replaygain_tags],
            settings.replayGain,
            enabled = !settings.bitPerfect,
        ) {
            onUpdate { current -> current.copy(replayGain = it) }
        }
    }
    item {
        SettingsSlider(
            strings[R.string.speed],
            strings.format(
                R.string.playback_speed_value,
                if (settings.bitPerfect) 1f else settings.speed,
            ),
            if (settings.bitPerfect) 1f else settings.speed,
            0.5f..2f,
            29,
            enabled = !settings.bitPerfect,
        ) {
            onUpdate { current -> current.copy(speed = (it * 20).roundToInt() / 20f) }
        }
    }
}

private fun LazyListScope.librarySettingsSection(
    settings: Settings,
    strings: TextResources,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onRefresh: () -> Unit,
    onIncluded: () -> Unit,
    onExcluded: () -> Unit,
    onDuplicates: () -> Unit,
) {
    item {
        SettingSwitch(
            strings[R.string.merge_artists],
            strings[R.string.merge_artists_description],
            settings.mergeArtists,
        ) {
            onUpdate { current -> current.copy(mergeArtists = it) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.artist_photos],
            strings[R.string.artist_photos_description],
            settings.artistPhotos,
        ) {
            onUpdate { current -> current.copy(artistPhotos = it) }
        }
    }
    item {
        SettingsAction(
            strings[R.string.refresh_music],
            strings[R.string.find_new_files_and_refresh_artwork],
            Icons.Default.Refresh,
            onRefresh,
        )
    }
    item { SectionTitle(strings[R.string.folders_title]) }
    item {
        SettingsAction(
            strings[R.string.included_folders],
            if (settings.includedFolders.isBlank()) strings[R.string.all_music]
            else
                strings.format(
                    R.string.selected_folders,
                    FolderRules.entries(settings.includedFolders).size,
                ),
            Icons.Default.Folder,
            onIncluded,
        )
    }
    item {
        SettingsAction(
            strings[R.string.excluded_folders],
            strings[R.string.hide_selected_folders_from_the_library],
            Icons.Default.FolderOff,
            onExcluded,
        )
    }
    item {
        SettingsAction(
            strings[R.string.duplicates_and_hidden_files],
            strings[R.string.compare_files_and_restore_hidden_music],
            Icons.Default.CopyAll,
            onDuplicates,
        )
    }
    item { SectionTitle(strings[R.string.filters], strings[R.string.choose_which_files_to_show]) }
    item {
        SettingsSlider(
            strings[R.string.minimum_duration],
            strings.format(R.string.s, settings.minimumSeconds),
            settings.minimumSeconds.toFloat(),
            0f..120f,
            23,
        ) {
            onUpdate { current -> current.copy(minimumSeconds = it.roundToInt()) }
        }
    }
}

private fun LazyListScope.historySettingsSection(
    settings: Settings,
    strings: TextResources,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onDemo: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    item { SectionTitle(strings[R.string.listening_statistics_title]) }
    item {
        SettingSwitch(
            strings[R.string.save_listening_statistics],
            strings[R.string.your_history_stays_on_your_device],
            settings.statistics,
        ) {
            onUpdate { current -> current.copy(statistics = it) }
        }
    }
    item { SectionTitle(strings[R.string.recaps]) }
    item {
        SettingsAction(
            strings[R.string.season_demo],
            strings[R.string.a_preview_of_recap_cards],
            Icons.Default.WbSunny,
        ) {
            onDemo(false)
        }
    }
    item {
        SettingsAction(
            strings[R.string.year_demo],
            strings[R.string.a_preview_of_your_year_in],
            Icons.Default.AutoAwesome,
        ) {
            onDemo(true)
        }
    }
    item { SectionTitle(strings[R.string.data_title]) }
    item {
        SettingsAction(
            strings[R.string.clear_history],
            strings[R.string.delete_listening_statistics],
            Icons.Default.DeleteOutline,
            onClear,
        )
    }
}

private fun LazyListScope.languageSettingsSection(
    settings: Settings,
    language: String,
    strings: TextResources,
    onLanguage: (String) -> Unit,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    item {
        SectionTitle(
            strings[R.string.app_language],
            strings[R.string.song_titles_and_lyrics_stay_in],
        )
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                    Triple(
                        "system",
                        strings[R.string.device_language],
                        strings[R.string.language_system_hint],
                    ),
                    Triple(
                        "ru",
                        strings[R.string.language_russian],
                        strings[R.string.language_ru_hint],
                    ),
                    Triple("en", "English", strings[R.string.language_en_hint]),
                )
                .forEach { (id, title, hint) ->
                    StyleChoice(
                        title,
                        hint,
                        language == id,
                        if (id == "system") Icons.Default.PhoneAndroid else Icons.Default.Translate,
                    ) {
                        onLanguage(id)
                    }
                }
        }
    }
    item { SectionTitle(strings[R.string.date_format], strings[R.string.date_format_hint]) }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppDates.formats.forEach { pattern ->
                StyleChoice(
                    if (pattern == "system") strings[R.string.system] else pattern,
                    AppDates.format(LocalDate.now(), pattern),
                    settings.dateFormat == pattern,
                    Icons.Default.CalendarMonth,
                ) {
                    onUpdate { it.copy(dateFormat = pattern) }
                }
            }
        }
    }
}

private fun LazyListScope.aboutSettingsSection(strings: TextResources) {
    item { FontLicense() }
    item {
        SettingsNote(
            strings.format(R.string.settings_screen_lad, BuildConfig.VERSION_NAME),
            strings[R.string.your_music_your_rhythm_no_accounts],
        )
    }
    item {
        Text(
            "MP3 · FLAC · AAC/M4A · OGG · Opus · WAV",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    item {
        Text(
            strings[R.string.material_3_expressive_lyrics_by_lrclib],
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun moveTab(tabs: String, key: String, delta: Int): String {
    val entries = tabs.split(',').distinct().toMutableList()
    val index = entries.indexOf(key)
    val destination = index + delta
    if (index >= 0 && destination in entries.indices)
        entries.add(destination, entries.removeAt(index))
    return entries.joinToString(",")
}
