package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class ArtistCreditsTest {
    @Test
    fun featuredAndJointCreditsMergeWithoutDroppingGuests() {
        val names =
            listOf(
                "PHARAOH",
                "pharaoh",
                "PHARAOH feat. Guest",
                "Guest & Pharaoh",
                "PHARAOH ft.Guest",
                "PHARAOH (featuring Guest)",
            )
        val groups = ArtistCredits(names).groups(names) { it }
        assertEquals(setOf("pharaoh", "guest"), groups.map { it.key }.toSet())
        assertEquals(6, groups.first { it.key == "pharaoh" }.items.size)
        assertEquals(4, groups.first { it.key == "guest" }.items.size)
        assertEquals("PHARAOH", groups.first { it.key == "pharaoh" }.name)
    }

    @Test
    fun separatorsUnicodeAndCapitalizationAreNormalized() {
        val names =
            listOf(
                "Artist",
                "Other",
                "ARTIST + Other",
                "Artist/Other",
                "Artist x Other",
                "Artist,Other",
                "Artist;Other",
                "Artist | Other",
                "Ａｒｔｉｓｔ feat: Other",
                "Artist with Other",
                "Artist и Other",
                "Artist при участии Other",
                "Artist &amp; Other",
            )
        val groups = ArtistCredits(names).groups(names) { it }
        assertEquals(2, groups.size)
        assertEquals(names.size - 1, groups.first { it.key == "artist" }.items.size)
    }

    @Test
    fun duplicateCreditsDoNotCountTheSameListenTwice() {
        val c = ArtistCredits(listOf("Artist feat. ARTIST & Other"))
        assertEquals(2, c.artists("Artist feat. ARTIST & Other").size)
    }

    @Test
    fun distinctNamesAndCompactBandNamesStayDistinct() {
        val c = ArtistCredits(listOf("AC/DC", "Artist", "Artist 2", "(Sic)", "XXXTENTACION"))
        assertEquals(listOf("AC/DC"), c.artists("AC/DC").map { it.name })
        assertEquals("(Sic)", c.artists("(Sic)").single().name)
        assertEquals(
            5,
            c.groups(listOf("AC/DC", "Artist", "Artist 2", "(Sic)", "XXXTENTACION")) { it }.size,
        )
    }

    @Test
    fun switchRestoresOriginalMetadataGrouping() {
        val names = listOf("Artist", "ARTIST", "Artist feat. Guest")
        assertEquals(3, ArtistCredits(names, false).groups(names) { it }.size)
    }

    @Test
    fun compactBandCaseVariantsAndMultiValueTagsStayConsistent() {
        val bands = listOf("AC/DC", "ac/dc", "Ac / Dc")
        assertEquals(1, ArtistCredits(bands).groups(bands) { it }.size)
        val credits =
            listOf(
                "Artist",
                "Other",
                "Artist vs. Other",
                "Artist — Other",
                "Artist w/ Other",
                "Artist\nOther",
                "Artist\u0000Other",
            )
        val groups = ArtistCredits(credits).groups(credits) { it }
        assertEquals(setOf("artist", "other"), groups.map { it.key }.toSet())
        assertEquals(credits.size - 1, groups.first { it.key == "artist" }.items.size)
    }
}
