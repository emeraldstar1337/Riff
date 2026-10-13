package dev.lad

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.PreferencesMigration
import dev.lad.domain.QueueEntry
import dev.lad.domain.QueueSnapshot
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Release09IntegrationTest {
    private val app
        get() =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as LadApplication

    @Test
    fun oldAudioDefaultsMigrateOnceAndPreserveOtherPreferences() = runBlocking {
        val migration = PreferencesMigration()
        val old =
            mutablePreferencesOf(
                intPreferencesKey("fade") to 3,
                booleanPreferencesKey("visualizer") to true,
                booleanPreferencesKey("stats") to true,
            )
        assertTrue(migration.shouldMigrate(old))
        val next = migration.migrate(old)
        assertEquals(0, next[intPreferencesKey("fade")])
        assertEquals(false, next[booleanPreferencesKey("visualizer")])
        assertEquals(true, next[booleanPreferencesKey("stats")])
        assertFalse(migration.shouldMigrate(next))
    }

    @Test
    fun simultaneousSettingsChangesAndSeekStyleSurviveRoundTrip() = runBlocking {
        val before = app.settings.settings.first()
        try {
            coroutineScope {
                val a = async { app.settings.update { it.copy(seekStyle = "wave_dot") } }
                val b = async { app.settings.update { it.copy(lyricsSize = 31f) } }
                a.await()
                b.await()
            }
            val saved = app.settings.settings.first()
            assertEquals("wave_dot", saved.seekStyle)
            assertEquals(31f, saved.lyricsSize)
        } finally {
            app.settings.update(before)
        }
    }

    @Test
    fun queuePositionIsRestoredFromItsSmallCheckpointStore() = runBlocking {
        val before = app.settings.queue.first()
        try {
            val queue = QueueSnapshot(entries = listOf(QueueEntry(10, 1)), playbackMs = 1234)
            app.settings.saveQueue(queue)
            app.settings.saveQueue(queue.copy(playbackMs = 4567))
            assertEquals(4567L, app.settings.queue.first()?.playbackMs)
        } finally {
            app.settings.saveQueue(before ?: QueueSnapshot())
        }
    }

    @Test
    fun bothLanguagesResolveDialogsErrorsAndRecapMessages() {
        val en = TextResources(AppLanguage.context(app, "en"))
        val ru = TextResources(AppLanguage.context(app, "ru"))
        assertEquals("Settings", en[R.string.settings])
        assertEquals("Настройки", ru[R.string.settings])
        assertEquals("Off", en[R.string.off])
        assertEquals("Выключен", ru[R.string.off])
        assertTrue(en.format(R.string.profile_volume, 75).endsWith("75%"))
        assertEquals("All tracks", en.label("Все треки"))
        assertEquals("Все треки", ru.label("All tracks"))
    }

    @Test
    fun riffBrandAndGroupingPreferenceUseRealAndroidResources() = runBlocking {
        val en = TextResources(AppLanguage.context(app, "en"))
        val ru = TextResources(AppLanguage.context(app, "ru"))
        assertEquals("Riff", en[R.string.app_name])
        assertEquals("Riff", ru[R.string.app_name])
        assertEquals("Artwork", en[R.string.artwork_title])
        assertEquals("Обложка", ru[R.string.artwork_title])
        val before = app.settings.settings.first()
        try {
            app.settings.update { it.copy(mergeArtists = false) }
            assertFalse(app.settings.settings.first().mergeArtists)
            app.settings.update { it.copy(mergeArtists = true) }
            assertTrue(app.settings.settings.first().mergeArtists)
        } finally {
            app.settings.update(before)
        }
    }
}
