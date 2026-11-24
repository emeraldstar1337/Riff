@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.lad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.data.*
import dev.lad.i18n.rememberText
import dev.lad.playback.PlaybackState

@Composable
fun AudioPanel(settings: Settings, playback: PlaybackState, vm: LadViewModel, onClose: () -> Unit) {
    val strings = rememberText()
    val device = playback.deviceKey
    var profile by
        remember(device) { mutableStateOf(SoundProfiles.read(settings.audioProfiles, device)) }
    fun save(p: dev.lad.domain.SoundProfile) {
        profile = p
        vm.playback.soundProfile(device, p)
    }
    PlayerSheet(
        strings[R.string.your_sound],
        strings.label(playback.deviceName),
        "audio",
        onClose,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(LadSpacing.page),
            verticalArrangement = Arrangement.spacedBy(LadSpacing.inline),
        ) {
            if (settings.bitPerfect) {
                BitPerfectInfo(playback.bitPerfect)
                Text(strings[R.string.bit_perfect_effects_bypassed])
                FilledTonalButton(
                    onClick = { vm.updateSettings { it.copy(bitPerfect = false) } },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.bit_perfect_disable])
                }
            } else {
                Text(
                    strings[R.string.this_profile_is_saved_separately_for],
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingSwitch(
                    strings[R.string.equalizer],
                    strings[R.string.adjusts_the_tone_boosting_a_band],
                    profile.enabled,
                ) {
                    save(profile.copy(enabled = it))
                }
                val presets = remember { SoundProfiles.presets.entries.toList() }
                ChoiceChips(
                    if (profile.enabled)
                        presets.firstOrNull { it.value == profile.bands }?.key.orEmpty()
                    else "",
                    presets.map { it.key to strings.label(it.key) },
                ) { selectedKey ->
                    presets.firstOrNull { it.key == selectedKey }?.let { entry ->
                        save(profile.copy(enabled = true, bands = entry.value))
                    }
                }
                Surface(
                    shape = LadShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(LadSpacing.medium)) {
                        Text(
                            strings[R.string.bands_db],
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz").forEachIndexed {
                            i,
                            label ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LadSpacing.small),
                            ) {
                                Text(
                                    label,
                                    Modifier.width(54.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Slider(
                                    profile.bands[i],
                                    { value ->
                                        save(
                                            profile.copy(
                                                enabled = true,
                                                bands =
                                                    profile.bands.mapIndexed { n, v ->
                                                        if (n == i) value else v
                                                    },
                                            )
                                        )
                                    },
                                    Modifier.weight(1f),
                                    valueRange = -12f..12f,
                                )
                                Text(
                                    strings.format(R.string.signed_decimal_one, profile.bands[i]),
                                    Modifier.width(42.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                Text(
                    strings.format(
                        R.string.balance,
                        if (profile.balance == 0f) strings[R.string.centred]
                        else if (profile.balance < 0) strings[R.string.left]
                        else strings[R.string.right],
                    )
                )
                SettingsSlider(
                    strings[R.string.balance_title],
                    strings.format(R.string.decimal_two, profile.balance),
                    profile.balance,
                    -1f..1f,
                ) {
                    save(profile.copy(balance = it))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(strings[R.string.audio_panel_left])
                    Text(strings[R.string.audio_panel_right])
                }
                Text(strings.format(R.string.profile_volume, (profile.volume * 100).toInt()))
                SettingsSlider(
                    strings[R.string.volume],
                    "${(profile.volume * 100).toInt()}%",
                    profile.volume,
                    0f..1f,
                ) {
                    save(profile.copy(volume = it))
                }
                TextButton(
                    onClick = { save(dev.lad.domain.SoundProfile()) },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.reset_profile])
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
