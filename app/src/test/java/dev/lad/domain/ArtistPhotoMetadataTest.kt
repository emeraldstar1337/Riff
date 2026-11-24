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

    @Test
    fun resolvesDeezerArtistAndRejectsDefaultPlaceholders() {
        val validUrl =
            "https://cdn-images.dzcdn.net/images/artist/3212498cc7cd91f282d934043746a64d/500x500-000000-80-0-0.jpg"
        val placeholderUrl =
            "https://cdn-images.dzcdn.net/images/artist//500x500-000000-80-0-0.jpg"
        val emptyMd5Url =
            "https://cdn-images.dzcdn.net/images/artist/d41d8cd98f00b204e9800998ecf8427e/500x500-000000-80-0-0.jpg"
        assertTrue(ArtistPhotoMetadata.isValidDeezerImage(validUrl))
        assertFalse(ArtistPhotoMetadata.isValidDeezerImage(placeholderUrl))
        assertFalse(ArtistPhotoMetadata.isValidDeezerImage(emptyMd5Url))
        assertFalse(ArtistPhotoMetadata.isValidDeezerImage("http://cdn-images.dzcdn.net/images/artist/abc/500x500.jpg"))

        val resolved =
            ArtistPhotoMetadata.resolveDeezer(
                "PHARAOH",
                listOf(
                    ArtistPhotoMetadata.DeezerArtist(1L, "Pharaoh", 100, placeholderUrl, ""),
                    ArtistPhotoMetadata.DeezerArtist(2L, "Pharaoh", 89000, validUrl, "https://www.deezer.com/artist/2"),
                    ArtistPhotoMetadata.DeezerArtist(3L, "Other", 500000, validUrl, ""),
                ),
            )
        assertEquals(2L, resolved?.id)
        assertEquals(validUrl, resolved?.image)

        val loose =
            ArtistPhotoMetadata.resolveDeezer(
                "Ёлка",
                listOf(
                    ArtistPhotoMetadata.DeezerArtist(4L, "Елка", 12000, validUrl, ""),
                ),
            )
        assertEquals(4L, loose?.id)
    }
}
