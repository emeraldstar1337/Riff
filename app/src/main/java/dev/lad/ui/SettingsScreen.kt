@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lad.BuildConfig
import dev.lad.R
import dev.lad.data.AppearancePresets
import dev.lad.data.Listen
import dev.lad.data.Settings
import dev.lad.domain.BitPerfectState
import dev.lad.domain.FolderRules
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import dev.lad.i18n.rememberText
import dev.lad.statistics.MusicStats
import dev.lad.statistics.RecapGenerator
import dev.lad.statistics.Season
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SettingsGroup(val title: Int) {
    APPEARANCE(R.string.settings_group_appearance),
    PLAYER_UI(R.string.settings_group_player),
    SOUND_LIBRARY(R.string.settings_group_sound_library),
    DATA_APP(R.string.settings_group_app),
}

private enum class SettingsPage(
    val group: SettingsGroup,
    val title: Int,
    val detail: Int,
    val icon: ImageVector,
    val keywords: List<Int> = emptyList(),
) {
    THEME(
        SettingsGroup.APPEARANCE,
        R.string.theme_and_colours,
        R.string.light_dark_monet_and_custom_palettes,
        Icons.Default.Palette,
        listOf(R.string.black_background, R.string.artwork_colours, R.string.system),
    ),
    SHAPES(
        SettingsGroup.APPEARANCE,
        R.string.shapes_and_motion,
        R.string.artwork_shapes_and_transitions,
        Icons.Default.Category,
        listOf(R.string.glow, R.string.album_atmosphere, R.string.abstract_shapes, R.string.motion),
    ),
    PLAYER(
        SettingsGroup.PLAYER_UI,
        R.string.player,
        R.string.settings_player_detail,
        Icons.Default.MusicNote,
        listOf(
            R.string.seek_bar,
            R.string.mini_player,
            R.string.vinyl_artwork,
            R.string.visualizer,
            R.string.widget_settings_title,
        ),
    ),
    LYRICS(
        SettingsGroup.PLAYER_UI,
        R.string.settings_lyrics_title,
        R.string.settings_lyrics_detail,
        Icons.AutoMirrored.Filled.Notes,
        listOf(R.string.lyrics_appearance, R.string.text_size, R.string.line_spacing),
    ),
    NAVIGATION(
        SettingsGroup.PLAYER_UI,
        R.string.navigation,
        R.string.tabs_and_their_order,
        Icons.Default.Dashboard,
        listOf(R.string.your_order),
    ),
    AUDIO(
        SettingsGroup.SOUND_LIBRARY,
        R.string.sound_and_devices,
        R.string.settings_audio_detail,
        Icons.Default.Headphones,
        listOf(
            R.string.bit_perfect_title,
            R.string.equalizer,
            R.string.crossfade,
            R.string.speed,
            R.string.pause_on_disconnect,
        ),
    ),
    LIBRARY(
        SettingsGroup.SOUND_LIBRARY,
        R.string.settings_screen_library,
        R.string.folders_duration_duplicates_and_hidden_files,
        Icons.Default.Folder,
        listOf(
            R.string.included_folders,
            R.string.excluded_folders,
            R.string.merge_artists,
            R.string.artist_photos,
            R.string.minimum_duration,
        ),
    ),
    HISTORY(
        SettingsGroup.DATA_APP,
        R.string.history_and_recaps,
        R.string.statistics_and_listening_recaps,
        Icons.Default.Insights,
        listOf(R.string.artwork_in_recaps, R.string.clear_history, R.string.recaps),
    ),
    LANGUAGE(
        SettingsGroup.DATA_APP,
        R.string.language,
        R.string.russian_english_or_device_language,
        Icons.Default.Language,
        listOf(R.string.date_format, R.string.app_language),
    ),
    ABOUT(
        SettingsGroup.DATA_APP,
        R.string.about_lad,
        R.string.version_and_features,
        Icons.Default.Info,
        listOf(R.string.font_license),
    ),
}

@Composable
fun SettingsScreen(
    settings: Settings,
    vm: LadViewModel,
    onDemo: (Boolean) -> Unit,
    onDuplicates: () -> Unit = {},
    onSeasonRecap: (MusicStats, String) -> Unit = { _, _ -> },
) {
    val strings = rememberText()
    val scope = rememberCoroutineScope()
    val animations = LocalLadSettings.current.animations
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var included by remember { mutableStateOf(false) }
    var excluded by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var seasonYear by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    val folders by vm.folders.collectAsStateWithLifecycle()
    val history by vm.statisticsState.history.collectAsStateWithLifecycle()
    val bitPerfectFlow =
        remember(vm) { vm.playback.state.map { it.bitPerfect }.distinctUntilChanged() }
    val bitPerfect by bitPerfectFlow.collectAsStateWithLifecycle(initialValue = BitPerfectState())
    val language by AppLanguage.selected.collectAsStateWithLifecycle()
    val cover =
        remember(vm) { vm.playback.state.map { it.current?.artwork }.distinctUntilChanged() }
    val artwork by cover.collectAsStateWithLifecycle(initialValue = null)
    val previewSeed = artworkSeed(artwork, settings.artworkColor)
    LaunchedEffect(page) {
        if (page == SettingsPage.LIBRARY) vm.loadFolders()
        if (page == SettingsPage.AUDIO) vm.playback.connect()
    }
    BackHandler(page != null && !included && !excluded && !clear) { page = null }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null && initialState == null
            val sign = if (forward) 1 else -1
            (fadeIn(LadMotion.defaultEffects(animations)) +
                    slideInHorizontally(LadMotion.defaultSpatial(animations)) { it / 8 * sign })
                .togetherWith(
                    fadeOut(LadMotion.fastEffects(animations)) +
                        slideOutHorizontally(LadMotion.defaultSpatial(animations)) {
                            -it / 10 * sign
                        }
                )
        },
        label = "settings page",
    ) { activePage ->
        val list = remember(activePage) { LazyListState() }
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(LadSpacing.page),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (activePage == null) {
                item("search") {
                    OutlinedTextField(
                        query,
                        { query = it },
                        Modifier.fillMaxWidth(),
                        placeholder = { Text(strings[R.string.find_a_setting]) },
                        shape = LadShapes.hero,
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon =
                            if (query.isNotEmpty()) {
                                {
                                    IconButton({ query = "" }) {
                                        Icon(Icons.Default.Close, strings[R.string.clear])
                                    }
                                }
                            } else null,
                    )
                }
                SettingsGroup.entries.forEach { group ->
                    val pages =
                        SettingsPage.entries.filter { section ->
                            section.group == group &&
                                (query.isBlank() ||
                                    buildString {
                                            append(strings[section.title]).append(' ')
                                            append(strings[section.detail]).append(' ')
                                            section.keywords.forEach { append(strings[it]).append(' ') }
                                            if (section == SettingsPage.THEME) append("Monet ")
                                            if (section == SettingsPage.AUDIO) append("ReplayGain ")
                                        }
                                        .contains(query, true))
                        }
                    if (pages.isNotEmpty()) {
                        item("group-${group.name}") {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                SettingsGroupLabel(strings[group.title])
                                Spacer(Modifier.height(4.dp))
                                pages.forEachIndexed { idx, section ->
                                    StyleChoice(
                                        strings[section.title],
                                        strings[section.detail],
                                        selected = false,
                                        icon = section.icon,
                                        shape = segmentedItemShape(idx, pages.size),
                                    ) {
                                        page = section
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item("back") {
                    TextButton(
                        { page = null },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.ChevronLeft, null)
                        Text(strings[R.string.all_settings])
                    }
                }
                item("title") { BitsTitle(strings[activePage.title]) }
                when (activePage) {
                    SettingsPage.THEME -> themeSettingsSection(settings, strings, vm::updateSettings)
                    SettingsPage.SHAPES ->
                        shapesSettingsSection(settings, previewSeed, strings, vm::updateSettings)
                    SettingsPage.PLAYER ->
                        playerSettingsSection(settings, strings, vm::updateSettings)
                    SettingsPage.LYRICS ->
                        lyricsSettingsSection(settings, strings, vm::updateSettings)
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
                            seasonYear = seasonYear,
                            onSeasonYearChange = { seasonYear = it },
                            onOpenSeason = { season ->
                                val chosenYear = seasonYear
                                scope.launch {
                                    val aggregated =
                                        withContext(Dispatchers.Default) {
                                            RecapGenerator.aggregate(
                                                history,
                                                RecapGenerator.season(season, chosenYear),
                                                mergeArtists = settings.mergeArtists,
                                            )
                                        }
                                    onSeasonRecap(
                                        aggregated,
                                        "${strings.label(season.label)} $chosenYear",
                                    )
                                }
                            },
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
            swatches = AccentColors,
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
}

private fun LazyListScope.shapesSettingsSection(
    settings: Settings,
    previewSeed: Color?,
    strings: TextResources,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
    item {
        SectionTitle(
            strings[R.string.artwork_title],
            strings[R.string.image_shapes_in_the_library_and],
        )
    }
    val shapeOptions =
        listOf(
            "rounded" to strings[R.string.rounded],
            "circle" to strings[R.string.circle],
            "flower" to strings[R.string.flower],
            "clover" to strings[R.string.clover_shape],
            "cookie" to strings[R.string.cookie_shape],
            "sunny" to strings[R.string.sunny_shape],
        )
    item {
        CoverShapeChoices(settings.coverShape, shapeOptions) {
            onUpdate { current -> current.copy(coverShape = it) }
        }
    }
    item {
        SettingSwitch(
            strings[R.string.cover_morph_title],
            strings[R.string.cover_morph_description],
            settings.coverMorph,
        ) {
            onUpdate { current -> current.copy(coverMorph = it) }
        }
    }
    item {
        AnimatedVisibility(
            visible = settings.coverMorph,
            enter = fadeIn(LadMotion.defaultEffects(settings.animations)) + expandVertically(),
            exit = fadeOut(LadMotion.fastEffects(settings.animations)) + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(
                    strings[R.string.pause_cover_shape_title],
                    strings[R.string.pause_cover_shape_subtitle],
                )
                CoverShapeChoices(settings.pauseCoverShape, shapeOptions) {
                    onUpdate { current -> current.copy(pauseCoverShape = it) }
                }
                PlayPauseMorphPreview(
                    pauseShape = settings.pauseCoverShape,
                    playShape = settings.coverShape,
                    animations = settings.animations,
                )
            }
        }
    }
    item { SectionTitle(strings[R.string.transitions], strings[R.string.interface_motion]) }
    item {
        SettingSwitch(
            strings[R.string.motion],
            strings[R.string.smooth_transitions_and_artwork_changes],
            settings.animations,
        ) {
            onUpdate { current -> current.copy(animations = it) }
        }
    }
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
        SectionTitle(
            strings[R.string.glow],
            strings[R.string.the_main_area_of_the_artwork],
        )
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
    item { GlowStylePreview(settings, previewSeed) }
    item {
        AnimatedVisibility(
            visible = settings.ambientBackground,
            enter = fadeIn(LadMotion.defaultEffects(settings.animations)) + expandVertically(),
            exit = fadeOut(LadMotion.fastEffects(settings.animations)) + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceChips(
                    settings.glowPreset,
                    AppearancePresets.glow.map { it.id to strings.label(it.name) },
                ) { id ->
                    onUpdate { current ->
                        AppearancePresets.glow.firstOrNull { it.id == id }?.apply(current)
                            ?: current
                    }
                }
                AppearanceSlider(strings[R.string.glow_intensity], settings.glowStrength, 0f..1.8f) {
                    onUpdate { current -> current.copy(glowPreset = "custom", glowStrength = it) }
                }
                AppearanceSlider(strings[R.string.glow_radius], settings.glowRadius, 0.5f..1.6f) {
                    onUpdate { current -> current.copy(glowPreset = "custom", glowRadius = it) }
                }
                AppearanceSlider(strings[R.string.saturation], settings.glowSaturation, 0f..1f) {
                    onUpdate { current -> current.copy(glowPreset = "custom", glowSaturation = it) }
                }
                SettingSwitch(
                    strings[R.string.animated_glow],
                    strings[R.string.moves_only_while_music_is_playing],
                    settings.glowMotion,
                ) {
                    onUpdate { current -> current.copy(glowMotion = it) }
                }
                SettingSwitch(
                    strings[R.string.abstract_shapes],
                    strings[R.string.three_soft_background_shapes_they_move],
                    settings.glowShapes,
                ) {
                    onUpdate { current -> current.copy(glowShapes = it) }
                }
                AnimatedVisibility(visible = settings.glowShapes) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle(strings[R.string.shape_type])
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
                        AppearanceSlider(strings[R.string.shape_scale], settings.shapeScale, 0.5f..1.6f) { value ->
                            onUpdate { it.copy(shapeScale = value) }
                        }
                        AppearanceSlider(
                            strings[R.string.shape_opacity],
                            settings.shapeOpacity,
                            0.02f..0.24f,
                        ) {
                            onUpdate { current -> current.copy(shapeOpacity = it) }
                        }
                        AppearanceSlider(
                            strings[R.string.motion_speed],
                            settings.motionSpeed,
                            0.3f..1.5f,
                        ) {
                            onUpdate { current -> current.copy(motionSpeed = it) }
                        }
                    }
                }
            }
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
            strings[R.string.mini_player],
            strings[R.string.size_and_playback_controls],
        )
    }
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
    item {
        SettingsNote(
            strings[R.string.widget_settings_title],
            strings[R.string.widget_settings_hint],
        )
    }
    item { WidgetAddButtons() }
}

private fun LazyListScope.lyricsSettingsSection(
    settings: Settings,
    strings: TextResources,
    onUpdate: ((Settings) -> Settings) -> Unit,
) {
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
    item { SectionTitle(strings[R.string.nav_style_title]) }
    item {
        ChoiceChips(
            settings.navStyle,
            listOf(
                "bar" to strings[R.string.nav_style_bar],
                "dock" to strings[R.string.nav_style_dock],
            ),
        ) { style ->
            onUpdate { it.copy(navStyle = style) }
        }
    }
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
    item { SectionTitle(strings[R.string.artists]) }
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
}

private fun LazyListScope.historySettingsSection(
    settings: Settings,
    strings: TextResources,
    seasonYear: Int,
    onSeasonYearChange: (Int) -> Unit,
    onOpenSeason: (Season) -> Unit,
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
    item { SectionTitle(strings[R.string.recaps], strings[R.string.statistics_card_appearance]) }
    item {
        SettingSwitch(
            strings[R.string.artwork_in_recaps],
            strings[R.string.recap_colours_follow_the_top_track],
            settings.recapArtwork,
        ) {
            onUpdate { current -> current.copy(recapArtwork = it) }
        }
    }
    item {
        SectionTitle(
            strings[R.string.season_recaps_title],
            strings[R.string.season_recaps_subtitle],
        )
    }
    item {
        Surface(
            shape = LadShapes.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = { if (seasonYear > 1970) onSeasonYearChange(seasonYear - 1) },
                        enabled = seasonYear > 1970,
                    ) {
                        Icon(Icons.Default.ChevronLeft, strings[R.string.previous_year])
                    }
                    Text(
                        seasonYear.toString(),
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontFeatureSettings = "tnum"
                            ),
                    )
                    IconButton(
                        onClick = {
                            if (seasonYear < LocalDate.now().year) onSeasonYearChange(seasonYear + 1)
                        },
                        enabled = seasonYear < LocalDate.now().year,
                    ) {
                        Icon(Icons.Default.ChevronRight, strings[R.string.next_year])
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Season.entries.forEachIndexed { idx, s ->
                        val itemShape =
                            when (idx) {
                                0 ->
                                    RoundedCornerShape(
                                        topStart = 24.dp,
                                        bottomStart = 24.dp,
                                        topEnd = 8.dp,
                                        bottomEnd = 8.dp,
                                    )
                                Season.entries.lastIndex ->
                                    RoundedCornerShape(
                                        topStart = 8.dp,
                                        bottomStart = 8.dp,
                                        topEnd = 24.dp,
                                        bottomEnd = 24.dp,
                                    )
                                else -> RoundedCornerShape(8.dp)
                            }
                        FilledTonalButton(
                            onClick = { onOpenSeason(s) },
                            modifier =
                                Modifier.weight(1f).defaultMinSize(minWidth = 1.dp, minHeight = 48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                            shapes =
                                ButtonDefaults.shapes(
                                    shape = itemShape,
                                    pressedShape = RoundedCornerShape(16.dp),
                                ),
                        ) {
                            Text(
                                strings.label(s.label),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    item { SectionTitle(strings[R.string.demo_recaps_title]) }
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
