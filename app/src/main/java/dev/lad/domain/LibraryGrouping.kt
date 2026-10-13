package dev.lad.domain

import dev.lad.data.Track
import java.util.Locale

fun Track.albumKey(): String =
    if (albumId > 0) "id:$albumId:${album.trim().lowercase(Locale.ROOT)}"
    else "name:${album.lowercase(Locale.ROOT)}:${artist.lowercase(Locale.ROOT)}"

fun groupAlbums(tracks: List<Track>): List<List<Track>> =
    tracks
        .groupBy { it.albumKey() }
        .values
        .map { album -> album.sortedBy { it.trackNumber } }
        .sortedBy { it.first().album.lowercase(Locale.ROOT) }
