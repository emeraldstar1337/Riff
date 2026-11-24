package dev.lad.ui.state

import android.content.Context
import dev.lad.data.Settings
import dev.lad.data.SettingsStore
import dev.lad.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsStateHolder(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val onLibraryRulesChanged: () -> Unit,
    private val scope: CoroutineScope,
) {
    val settings: StateFlow<Settings> =
        settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, Settings())

    fun updateSettings(transform: (Settings) -> Settings) {
        scope.launch {
            var rulesChanged = false
            settingsStore.update { current ->
                val next = transform(current)
                rulesChanged =
                    next.includedFolders != current.includedFolders ||
                        next.excludedFolders != current.excludedFolders ||
                        next.minimumSeconds != current.minimumSeconds
                next
            }
            if (rulesChanged) onLibraryRulesChanged()
        }
    }

    fun setLanguage(language: String) = AppLanguage.set(context, language)
}
