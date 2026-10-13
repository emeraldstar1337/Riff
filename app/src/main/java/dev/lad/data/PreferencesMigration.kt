package dev.lad.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

internal class PreferencesMigration : DataMigration<Preferences> {
    private val migrated = booleanPreferencesKey("audio_defaults_v9")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[migrated] != true

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            this[intPreferencesKey("fade")] = 0
            this[booleanPreferencesKey("visualizer")] = false
            this[booleanPreferencesKey("glowShapes")] = false
            this[booleanPreferencesKey("glowMotion")] = false
            this[migrated] = true
        }

    override suspend fun cleanUp() = Unit
}
