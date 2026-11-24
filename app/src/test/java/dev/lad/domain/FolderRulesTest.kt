package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class FolderRulesTest {
    @Test
    fun allowOneDirectoryWithChildrenButNotSimilarNames() {
        assertTrue(FolderRules.accepts("Music/Artist/", "Music", ""))
        assertFalse(FolderRules.accepts("MusicOther/", "Music", ""))
        assertFalse(FolderRules.accepts("Downloads/", "Music", ""))
    }

    @Test
    fun exclusionsWinAndRespectDirectoryBoundaries() {
        assertFalse(FolderRules.accepts("Music/Recordings/Live", "Music", "Music/Recordings"))
        assertTrue(FolderRules.accepts("Music/RecordingsOther", "Music", "Music/Recordings"))
    }

    @Test
    fun absolutePathsCaseAndEmptyAllowList() {
        assertTrue(FolderRules.accepts("/storage/emulated/0/Music/Album/", "music", ""))
        assertTrue(FolderRules.accepts("Podcasts", "", ""))
        assertFalse(FolderRules.accepts("Podcasts", "", "Podcasts"))
        assertTrue(FolderRules.accepts("Downloads", "Music\nDownloads", ""))
    }

    @Test
    fun pickedFoldersBindToVolumeAndRejectSiblings() {
        val rule = FolderRules.fromDocumentId("primary:Music/Allowed")!!
        assertTrue(FolderRules.accepts("@primary/Music/Allowed/Album", rule, ""))
        assertFalse(FolderRules.accepts("@primary/Music/Allowed2", rule, ""))
        assertFalse(FolderRules.accepts("@abcd-1234/Music/Allowed", rule, ""))
        assertTrue(FolderRules.accepts("/storage/emulated/0/Music/Allowed", rule, ""))
        assertTrue(FolderRules.accepts("Music/Allowed/", rule, ""))
        assertFalse(FolderRules.accepts("@primary/Music/Allowed/Hide", rule, rule + "/Hide"))
    }

    @Test
    fun legacyRelativeRulesAndRootSelectionsWork() {
        assertTrue(FolderRules.accepts("@abcd/Music/Album", "Music", ""))
        assertFalse(FolderRules.accepts("@abcd/MusicOther", "Music", ""))
        assertEquals("@primary", FolderRules.fromDocumentId("primary:"))
        assertTrue(FolderRules.accepts("@primary/Music", "@primary", ""))
        assertFalse(FolderRules.accepts("@abcd/Music", "@primary", ""))
        assertNull(FolderRules.fromDocumentId("cloud"))
    }
}
