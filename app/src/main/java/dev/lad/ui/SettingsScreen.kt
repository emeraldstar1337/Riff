package dev.lad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lad.BuildConfig
import dev.lad.R
import dev.lad.data.AppearancePresets
import dev.lad.data.Settings
import dev.lad.i18n.rememberText
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        shape = LadShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(description) },
            trailingContent = { Switch(checked, onChange) },
            colors =
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
        )
    }
}

@Composable
fun ChoiceChips(current: String, values: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { (value, name) ->
            val selected = current == value
            FilterChip(
                selected = selected,
                onClick = { onSelect(value) },
                label = {
                    Text(name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                },
                leadingIcon =
                    if (selected) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null,
                shape = RoundedCornerShape(if (selected) 16.dp else 24.dp),
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

private enum class SettingsPage(
    val title: Int,
    val detail: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
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
    val language by dev.lad.i18n.AppLanguage.selected.collectAsStateWithLifecycle()
    val cover =
        remember(vm) { vm.playback.state.map { it.current?.artwork }.distinctUntilChanged() }
    val artwork by cover.collectAsStateWithLifecycle(initialValue = null)
    val previewSeed = artworkSeed(artwork, settings.artworkColor)
    val list = remember(page) { LazyListState() }
    LaunchedEffect(page) { if (page == SettingsPage.LIBRARY) vm.loadFolders() }
    BackHandler(page != null && !included && !excluded && !clear) { page = null }

    LazyColumn(
        state = list,
        contentPadding = PaddingValues(LadSpacing.page),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (page == null) {
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
                TextButton({ page = null }) {
                    Icon(Icons.Default.ChevronLeft, null)
                    Text(strings[R.string.all_settings])
                }
            }
            item { BitsTitle(strings[page!!.title]) }
            when (page!!) {
                SettingsPage.THEME -> {
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
                            vm.updateSettings { current -> current.copy(theme = it) }
                        }
                    }
                    item {
                        SettingSwitch(
                            "Monet",
                            strings[R.string.colours_from_your_wallpaper],
                            settings.dynamicColor,
                        ) {
                            vm.updateSettings { current -> current.copy(dynamicColor = it) }
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
                            vm.updateSettings { current ->
                                current.copy(accent = it, dynamicColor = false)
                            }
                        }
                    }
                    item {
                        SettingSwitch(
                            strings[R.string.black_background],
                            strings[R.string.deep_black_in_the_dark_theme],
                            settings.pureBlack,
                        ) {
                            vm.updateSettings { current -> current.copy(pureBlack = it) }
                        }
                    }
                    item {
                        SettingSwitch(
                            strings[R.string.artwork_colours],
                            strings[R.string.player_colours_follow_the_music],
                            settings.artworkColor,
                        ) {
                            vm.updateSettings { current -> current.copy(artworkColor = it) }
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
                            vm.updateSettings { current -> current.copy(recapArtwork = it) }
                        }
                    }
                }
                SettingsPage.SHAPES -> {
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
                            vm.updateSettings {
                                AppearancePresets.glow.first { it.id == id }.apply(it)
                            }
                        }
                    }
                    item {
                        AppearanceSlider(
                            strings[R.string.glow_intensity],
                            settings.glowStrength,
                            0f..1.8f,
                        ) {
                            vm.updateSettings { current ->
                                current.copy(glowPreset = "custom", glowStrength = it)
                            }
                        }
                    }
                    item {
                        AppearanceSlider(
                            strings[R.string.glow_radius],
                            settings.glowRadius,
                            0.5f..1.6f,
                        ) {
                            vm.updateSettings { current ->
                                current.copy(glowPreset = "custom", glowRadius = it)
                            }
                        }
                    }
                    item {
                        AppearanceSlider(
                            strings[R.string.saturation],
                            settings.glowSaturation,
                            0f..1f,
                        ) {
                            vm.updateSettings { current ->
                                current.copy(glowPreset = "custom", glowSaturation = it)
                            }
                        }
                    }
                    item {
                        SettingSwitch(
                            strings[R.string.animated_glow],
                            strings[R.string.moves_only_while_music_is_playing],
                            settings.glowMotion,
                        ) {
                            vm.updateSettings { current -> current.copy(glowMotion = it) }
                        }
                    }
                    item {
                        SettingSwitch(
                            strings[R.string.abstract_shapes],
                            strings[R.string.three_soft_background_shapes_they_move],
                            settings.glowShapes,
                        ) {
                            vm.updateSettings { current -> current.copy(glowShapes = it) }
                        }
                    }
                    item {
                        AppearanceSlider(
                            strings[R.string.shape_opacity],
                            settings.shapeOpacity,
                            0.02f..0.16f,
                        ) {
                            vm.updateSettings { current -> current.copy(shapeOpacity = it) }
                        }
                    }
                    item {
                        AppearanceSlider(
                            strings[R.string.motion_speed],
                            settings.motionSpeed,
                            0.3f..1.5f,
                        ) {
                            vm.updateSettings { current -> current.copy(motionSpeed = it) }
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
                                "square" to strings[R.string.square],
                                "circle" to strings[R.string.circle],
                                "flower" to strings[R.string.flower],
                            ),
                        ) {
                            vm.updateSettings { current -> current.copy(coverShape = it) }
                        }
                    }
                    item {
                        SectionTitle(
                            strings[R.string.transitions],
                            strings[R.string.interface_motion],
                        )
                    }
                    item {
                        SettingSwitch(
                            strings[R.string.motion],
                            strings[R.string.smooth_transitions_and_artwork_changes],
                            settings.animations,
                        ) {
                            vm.updateSettings { current -> current.copy(animations = it) }
                        }
                    }
                    item {
                        SettingSwitch(
                            strings[R.string.album_atmosphere],
                            strings[R.string.a_gentle_glow_in_the_artwork],
                            settings.ambientBackground,
                        ) {
                            vm.updateSettings { current -> current.copy(ambientBackground = it) }
                        }
                    }
                }
                SettingsPage.PLAYER -> {
                    item {
                        SectionTitle(
                            strings[R.string.seek_bar],
                            strings[R.string.choose_the_track_and_handle_shapes],
                        )
                    }
                    item {
                        SeekStyleChoices(settings.seekStyle) {
                            vm.updateSettings { current -> current.copy(seekStyle = it) }
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
                            vm.updateSettings {
                                AppearancePresets.lyrics.first { it.id == id }.apply(it)
                            }
                        }
                    }
                    item {
                        AppearanceSlider(
                            strings[R.string.text_size],
                            settings.lyricsSize,
                            16f..34f,
                        ) {
                            vm.updateSettings { current ->
                                current.copy(lyricsPreset = "custom", lyricsSize = it)
                            }
                        }
                    }
                    item {
                        AppearanceSlider(
                            strings[R.string.line_spacing],
                            settings.lyricsSpacing,
                            1.1f..1.7f,
                        ) {
                            vm.updateSettings { current ->
                                current.copy(lyricsPreset = "custom", lyricsSpacing = it)
                            }
                        }
                    }
                    item {
                        AppearanceSlider(
                            strings[R.string.inactive_line_brightness],
                            settings.lyricsDim,
                            0.2f..0.6f,
                        ) {
                            vm.updateSettings { current ->
                                current.copy(lyricsPreset = "custom", lyricsDim = it)
                            }
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
                            vm.updateSettings { current ->
                                current.copy(lyricsPreset = "custom", lyricsAlignment = it)
                            }
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
                            vm.updateSettings { current -> current.copy(playerStyle = it) }
                        }
                    }
                    item {
                        SettingSwitch(
                            strings[R.string.vinyl_artwork],
                            strings[R.string.rotates_during_playback],
                            settings.vinyl,
                        ) {
                            vm.updateSettings { current -> current.copy(vinyl = it) }
                        }
                    }
                    item {
                        SettingSwitch(
                            strings[R.string.visualizer],
                            strings[R.string.the_wave_responds_to_the_audio],
                            settings.visualizer,
                        ) {
                            vm.updateSettings { current -> current.copy(visualizer = it) }
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
                        ) {
                            vm.updateSettings { current ->
                                current.copy(crossfadeSeconds = it.toInt())
                            }
                        }
                    }
                    item {
                        SettingSwitch(
                            strings[R.string.pause_on_disconnect],
                            strings[R.string.when_an_audio_device_disconnects],
                            settings.pauseOnDisconnect,
                        ) {
                            vm.updateSettings { current -> current.copy(pauseOnDisconnect = it) }
                        }
                    }
                }
                SettingsPage.MINI -> {
                    item { MiniStylePreview(settings) }
                    listOf(
                            Triple("slim", R.string.slim, R.string.a_compact_row_with_music_and),
                            Triple("card", R.string.card, R.string.artwork_title_and_a_large_pause),
                            Triple(
                                "expanded",
                                R.string.with_time,
                                R.string.a_card_with_playback_time_and,
                            ),
                        )
                        .forEach { (id, title, detail) ->
                            item {
                                StyleChoice(
                                    strings[title],
                                    strings[detail],
                                    settings.miniStyle == id,
                                    Icons.Default.PlayCircle,
                                ) {
                                    vm.updateSettings { it.copy(miniStyle = id) }
                                }
                            }
                        }
                }
                SettingsPage.NAVIGATION -> {
                    val names =
                        mapOf(
                            "home" to strings[R.string.home],
                            "library" to strings[R.string.music],
                            "playlists" to strings[R.string.playlists],
                            "stats" to strings[R.string.history],
                        )
                    val selected =
                        settings.navigationTabs.split(',').filter { it in names }.distinct()
                    item { NavigationPreview(selected, names) }
                    item {
                        SettingsNote(
                            strings[R.string.your_order],
                            strings[R.string.enable_the_sections_you_need_and],
                        )
                    }
                    (selected + names.keys.filter { it !in selected }).forEach { key ->
                        item {
                            Surface(
                                shape = LadShapes.card,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        key in selected,
                                        { checked ->
                                            vm.updateSettings { current ->
                                                val before =
                                                    current.navigationTabs.split(',').distinct()
                                                val next =
                                                    if (checked) (before + key).distinct()
                                                    else before - key
                                                current.copy(
                                                    navigationTabs =
                                                        next.ifEmpty { before }.joinToString(",")
                                                )
                                            }
                                        },
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            names.getValue(key),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            if (key in selected)
                                                strings.format(
                                                    R.string.position,
                                                    selected.indexOf(key) + 1,
                                                )
                                            else strings[R.string.hidden_from_navigation],
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (key in selected) {
                                        IconButton(
                                            {
                                                vm.updateSettings {
                                                    it.copy(
                                                        navigationTabs =
                                                            moveTab(it.navigationTabs, key, -1)
                                                    )
                                                }
                                            },
                                            enabled = selected.indexOf(key) > 0,
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowUp,
                                                strings[R.string.move_up],
                                            )
                                        }
                                        IconButton(
                                            {
                                                vm.updateSettings {
                                                    it.copy(
                                                        navigationTabs =
                                                            moveTab(it.navigationTabs, key, 1)
                                                    )
                                                }
                                            },
                                            enabled = selected.indexOf(key) < selected.lastIndex,
                                        ) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                strings[R.string.move_down],
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                SettingsPage.AUDIO -> {
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
                        ) {
                            vm.audioOpen.value = true
                        }
                    }
                    item {
                        SectionTitle(strings[R.string.playback], strings[R.string.volume_and_tempo])
                    }
                    item {
                        SettingSwitch(
                            "ReplayGain",
                            strings[R.string.adjust_volume_using_replaygain_tags],
                            settings.replayGain,
                        ) {
                            vm.updateSettings { current -> current.copy(replayGain = it) }
                        }
                    }
                    item {
                        SettingsSlider(
                            strings[R.string.speed],
                            strings.format(R.string.playback_speed_value, settings.speed),
                            settings.speed,
                            0.5f..2f,
                            29,
                        ) {
                            vm.updateSettings { current ->
                                current.copy(speed = (it * 20).roundToInt() / 20f)
                            }
                        }
                    }
                }
                SettingsPage.LIBRARY -> {
                    item {
                        SettingSwitch(
                            strings[R.string.merge_artists],
                            strings[R.string.merge_artists_description],
                            settings.mergeArtists,
                        ) {
                            vm.updateSettings { current -> current.copy(mergeArtists = it) }
                        }
                    }
                    item {
                        SettingsAction(
                            strings[R.string.refresh_music],
                            strings[R.string.find_new_files_and_refresh_artwork],
                            Icons.Default.Refresh,
                            vm::refresh,
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
                                    dev.lad.domain.FolderRules.entries(settings.includedFolders)
                                        .size,
                                ),
                            Icons.Default.Folder,
                        ) {
                            included = true
                        }
                    }
                    item {
                        SettingsAction(
                            strings[R.string.excluded_folders],
                            strings[R.string.hide_selected_folders_from_the_library],
                            Icons.Default.FolderOff,
                        ) {
                            excluded = true
                        }
                    }
                    item {
                        SettingsAction(
                            strings[R.string.duplicates_and_hidden_files],
                            strings[R.string.compare_files_and_restore_hidden_music],
                            Icons.Default.CopyAll,
                            onDuplicates,
                        )
                    }
                    item {
                        SectionTitle(
                            strings[R.string.filters],
                            strings[R.string.choose_which_files_to_show],
                        )
                    }
                    item {
                        SettingsSlider(
                            strings[R.string.minimum_duration],
                            strings.format(R.string.s, settings.minimumSeconds),
                            settings.minimumSeconds.toFloat(),
                            0f..120f,
                            23,
                        ) {
                            vm.updateSettings { current ->
                                current.copy(minimumSeconds = it.roundToInt())
                            }
                        }
                    }
                }
                SettingsPage.HISTORY -> {
                    item { SectionTitle(strings[R.string.listening_statistics_title]) }
                    item {
                        SettingSwitch(
                            strings[R.string.save_listening_statistics],
                            strings[R.string.your_history_stays_on_your_device],
                            settings.statistics,
                        ) {
                            vm.updateSettings { current -> current.copy(statistics = it) }
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
                        ) {
                            clear = true
                        }
                    }
                }
                SettingsPage.LANGUAGE -> {
                    item {
                        SectionTitle(
                            strings[R.string.app_language],
                            strings[R.string.song_titles_and_lyrics_stay_in],
                        )
                    }
                    item {
                        ChoiceChips(
                            language,
                            listOf(
                                "system" to strings[R.string.device_language],
                                "ru" to strings[R.string.language_russian],
                                "en" to "English",
                            ),
                            vm::setLanguage,
                        )
                    }
                }
                SettingsPage.ABOUT -> {
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
                TextButton({
                    vm.clearStatistics()
                    clear = false
                }) {
                    Text(strings[R.string.clear])
                }
            },
            dismissButton = { TextButton({ clear = false }) { Text(strings[R.string.cancel]) } },
        )
}

private fun moveTab(tabs: String, key: String, delta: Int): String {
    val entries = tabs.split(',').distinct().toMutableList()
    val index = entries.indexOf(key)
    val destination = index + delta
    if (index >= 0 && destination in entries.indices)
        entries.add(destination, entries.removeAt(index))
    return entries.joinToString(",")
}

@Composable
private fun AppearanceSlider(
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
                onValueChangeFinished = { onSave(draft) },
            )
        }
    }
}

@Composable
private fun SettingsAction(
    title: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) = StyleChoice(title, detail, false, icon, onClick)
