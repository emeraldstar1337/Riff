package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class ArtistPhotoMetadataTest {
    @Test
    fun resolvesNamesakesUsingAnyLocalAlbumInsteadOfOnlyTheFirstTrack() {
        val songs =
            listOf(
                ArtistPhotoMetadata.Recording(10, "PHARAOH", "Song", "Pink Phloyd"),
                ArtistPhotoMetadata.Recording(20, "Pharaoh", "Other", "Different Album"),
            )
        assertEquals(
            10L,
            ArtistPhotoMetadata.resolve(
                "pharaoh",
                songs,
                listOf("Unreleased Demo" to "Unknown album", "Local Title" to "Pink Phloyd"),
            ),
        )
    }

    @Test
    fun resolvesSingleCatalogArtistDespiteUnreleasedLocalTitles() {
        val songs =
            listOf(
                ArtistPhotoMetadata.Recording(10, "Artist", "Catalog title", "Album"),
                ArtistPhotoMetadata.Recording(30, "Other Artist", "Artist", "Album"),
            )
        assertEquals(10L, ArtistPhotoMetadata.resolve("ARTIST", songs, listOf("Demo" to "Demo")))
    }

    @Test
    fun doesNotGuessWhenNamesakesHaveNoMatchingMetadata() {
        val songs =
            listOf(
                ArtistPhotoMetadata.Recording(10, "Artist", "One", "A"),
                ArtistPhotoMetadata.Recording(20, "Artist", "Two", "B"),
            )
        assertNull(ArtistPhotoMetadata.resolve("Artist", songs, emptyList()))
        assertNull(ArtistPhotoMetadata.resolve("Artist", songs, listOf("One" to "A", "Two" to "B")))
    }

    @Test
    fun repeatedCatalogEntriesDoNotOutweighMatchingLocalMetadata() {
        val songs =
            List(20) { ArtistPhotoMetadata.Recording(10, "Artist", "One", "A") } +
                listOf(ArtistPhotoMetadata.Recording(20, "Artist", "Two", "B"))
        assertEquals(20L, ArtistPhotoMetadata.resolve("Artist", songs, listOf("Two" to "B")))
    }

    @Test
    fun matchesCaseAndUnicodeWithoutConflatingDifferentArtists() {
        val artists = listOf(1L to "PHARAOH", 2L to "Pharaoh", 3L to "Pharaohs", 4L to "Radio Head")
        assertEquals(listOf(1L, 2L), ArtistPhotoMetadata.matchingIds(" pharaoh ", artists))
        assertTrue(ArtistPhotoMetadata.matchingIds("Radiohead", artists).isEmpty())
        assertEquals(ArtistPhotoMetadata.name("Beyoncé"), ArtistPhotoMetadata.name("BEYONCE\u0301"))
    }

    @Test
    fun readsPublicArtistArtworkWithEitherAttributeOrder() {
        val image =
            "https://is1-ssl.mzstatic.com/image/thumb/AMCArtistImages211/v4/profile.png/1200x630cw.png"
        val expected =
            "https://is1-ssl.mzstatic.com/image/thumb/AMCArtistImages211/v4/profile.png/512x512bb.jpg"
        assertEquals(
            expected,
            ArtistPhotoMetadata.image("<meta property=\"og:image\" content=\"$image\">"),
        )
        assertEquals(
            expected,
            ArtistPhotoMetadata.image("<META content='$image' property='og:image'>"),
        )
    }

    @Test
    fun ignoresUntrustedOrMissingImages() {
        for (image in
            listOf(
                "file:///tmp/image.png",
                "https://mzstatic.com.evil.test/image/thumb/a.jpg",
                "http://is1-ssl.mzstatic.com/image/thumb/a.jpg",
                "https://user@is1-ssl.mzstatic.com/image/thumb/a.jpg",
            )) {
            assertNull(ArtistPhotoMetadata.image("<meta property='og:image' content='$image'>"))
        }
        assertNull(ArtistPhotoMetadata.image("<meta property='og:title' content='Artist'>"))
    }
}
