package dev.lad

import android.content.ComponentName
import android.os.Bundle
import androidx.media3.session.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.*
import dev.lad.domain.FolderRules
import dev.lad.playback.PlaybackService
import dev.lad.ui.LadViewModel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class Folder08IntegrationTest {
    @Test
    fun savedRulesSurviveStartupRefreshAndExcludeSiblingFolders() = runBlocking {
        val i = InstrumentationRegistry.getInstrumentation()
        val app = i.targetContext.applicationContext as LadApplication
        val before = app.settings.settings.first()
        val rules =
            before.copy(
                minimumSeconds = 0,
                includedFolders = "@primary/Music/Allowed",
                excludedFolders = "@primary/Music/Allowed/Hidden",
            )
        try {
            app.settings.update(rules)
            app.music.scan(rules)
            val expected = app.music.dao.availableTracks()
            assertTrue(expected.isNotEmpty())
            assertTrue(
                expected.all {
                    FolderRules.accepts(
                        FolderRules.folder(it.path),
                        rules.includedFolders,
                        rules.excludedFolders,
                    )
                }
            )
            val vm = LadViewModel(app)
            i.runOnMainSync { vm.refresh(false) }
            delay(3000)
            val rows = app.music.dao.availableTracks()
            assertEquals(expected.map { it.id }.toSet(), rows.map { it.id }.toSet())
            assertTrue(
                rows.none {
                    it.path.contains("Allowed2") ||
                        it.path.contains("Hidden") ||
                        it.path.contains("Elsewhere")
                }
            )
        } finally {
            app.settings.update(before)
            app.music.scan(before)
        }
    }

    @Test
    fun queuedScanUsesLatestPersistedRulesInsteadOfStaleDefaults() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as LadApplication
        val before = app.settings.settings.first()
        val rules =
            before.copy(
                minimumSeconds = 0,
                includedFolders = "@primary/Music/Allowed",
                excludedFolders = "",
            )
        try {
            app.settings.update(rules)
            app.music.scan(
                Settings(minimumSeconds = 0),
                currentSettings = { app.settings.settings.first() },
            )
            val rows = app.music.dao.availableTracks()
            assertTrue(rows.isNotEmpty())
            assertTrue(
                rows.all {
                    FolderRules.accepts(
                        FolderRules.folder(it.path),
                        rules.includedFolders,
                        rules.excludedFolders,
                    )
                }
            )
        } finally {
            app.settings.update(before)
            app.music.scan(before)
        }
    }

    @Test
    fun changingRulesPrunesExistingQueueAndItsRepeatSourceImmediately() = runBlocking {
        val i = InstrumentationRegistry.getInstrumentation()
        val app = i.targetContext.applicationContext as LadApplication
        val before = app.settings.settings.first()
        val open = before.copy(includedFolders = "", excludedFolders = "", minimumSeconds = 0)
        app.settings.update(open)
        app.music.scan(open)
        val tracks = app.music.dao.availableTracks()
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        i.runOnMainSync {
            future =
                MediaController.Builder(
                        app,
                        SessionToken(app, ComponentName(app, PlaybackService::class.java)),
                    )
                    .buildAsync()
        }
        val controller = future!!.get(60, TimeUnit.SECONDS)
        try {
            var result: com.google.common.util.concurrent.ListenableFuture<SessionResult>? = null
            i.runOnMainSync {
                result =
                    controller.sendCustomCommand(
                        SessionCommand("dev.lad.CONTROL", Bundle.EMPTY),
                        Bundle().apply {
                            putString("action", "start")
                            putLongArray("ids", tracks.map { it.id }.toLongArray())
                            putLong("selected", tracks.first().id)
                        },
                    )
            }
            assertEquals(0, result!!.get(60, TimeUnit.SECONDS).resultCode)
            i.runOnMainSync { controller.pause() }
            val rules =
                open.copy(
                    includedFolders = "@primary/Music/Allowed",
                    excludedFolders = "@primary/Music/Allowed/Hidden",
                )
            val allowed =
                tracks
                    .filter {
                        FolderRules.accepts(it.path, rules.includedFolders, rules.excludedFolders)
                    }
                    .map { it.id }
                    .toSet()
            assertTrue(allowed.isNotEmpty())
            assertTrue(allowed.size < tracks.size)
            app.settings.update(rules)
            withTimeout(15000) {
                while (app.playback.state.value.queue.source.any { it !in allowed }) delay(100)
            }
            assertTrue(app.playback.state.value.queue.entries.all { it.trackId in allowed })
            assertEquals(allowed, app.playback.state.value.queue.source.toSet())
            assertNull(app.playback.state.value.error)
        } finally {
            i.runOnMainSync {
                controller.pause()
                controller.release()
            }
            app.settings.update(before)
            app.music.scan(before)
        }
    }
}
