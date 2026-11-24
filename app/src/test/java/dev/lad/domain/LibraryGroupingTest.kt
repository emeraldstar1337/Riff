package dev.lad.domain

import dev.lad.data.Track
import org.junit.Assert.*
import org.junit.Test

class LibraryGroupingTest {
    private fun track(id: Long, artist: String, albumId: Long = 7, album: String = "Альбом") =
        Track(
            id,
            "content://music/$id",
            "Песня $id",
            artist,
            album,
            albumId,
            180000,
            trackNumber = id.toInt(),
        )

    @Test
    fun guestsDoNotSplitTheSameAlbum() {
        val main = track(1, "Артист")
        val feat = track(2, "Артист, Гость")
        assertEquals(listOf(listOf(main, feat)), groupAlbums(listOf(feat, main)))
    }

    @Test
    fun missingIdsDoNotMergeUnrelatedRecords() {
        assertEquals(
            2,
            groupAlbums(
                    listOf(track(1, "Один", 0, "Без альбома"), track(2, "Другой", 0, "Без альбома"))
                )
                .size,
        )
    }

    @Test
    fun editedAlbumNamesRemainSeparateEvenWithTheOriginalMediaStoreId() {
        assertEquals(
            2,
            groupAlbums(listOf(track(1, "Артист"), track(2, "Артист", album = "Новая подборка")))
                .size,
        )
    }
}
