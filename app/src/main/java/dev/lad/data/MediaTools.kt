package dev.lad.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dev.lad.R
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import dev.lad.util.attempt
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

data class FileDetails(
    val filename: String = "",
    val size: Long = 0,
    val bitrate: Int = 0,
    val sampleRate: String = "",
    val bits: String = "",
    val channels: String = "",
    val mime: String = "",
)

class MediaTools(private val context: Context) {
    private val strings
        get() = TextResources(AppLanguage.context(context))

    suspend fun details(track: Track): FileDetails =
        withContext(Dispatchers.IO) {
            val uri = track.uri.toUri()
            var name = uri.lastPathSegment?.takeIf { it.contains('.') } ?: track.title
            var size = track.fileSize
            context.contentResolver
                .query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )
                ?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0) ?: name
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
            val m = MediaMetadataRetriever()
            try {
                m.setDataSource(context, uri)
                fun value(key: Int) = m.extractMetadata(key) ?: ""
                var sampleRate =
                    if (android.os.Build.VERSION.SDK_INT >= 31)
                        value(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    else ""
                var bits =
                    if (android.os.Build.VERSION.SDK_INT >= 31)
                        value(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    else ""
                var channels = ""
                val extractor = android.media.MediaExtractor()
                try {
                    extractor.setDataSource(context, uri, null)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        if (
                            format
                                .getString(android.media.MediaFormat.KEY_MIME)
                                ?.startsWith("audio/") != true
                        )
                            continue
                        if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE))
                            sampleRate =
                                format
                                    .getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                                    .toString()
                        if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT))
                            channels =
                                format
                                    .getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                                    .toString()
                        break
                    }
                } catch (_: Exception) {} finally {
                    extractor.release()
                }
                FileDetails(
                    name,
                    size,
                    value(MediaMetadataRetriever.METADATA_KEY_BITRATE).toIntOrNull() ?: 0,
                    sampleRate,
                    bits,
                    channels,
                    value(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                )
            } finally {
                m.release()
            }
        }

    suspend fun duplicates(tracks: List<Track>, onProgress: (Int, Int) -> Unit): List<List<Track>> =
        withContext(Dispatchers.IO) {
            val candidates =
                tracks
                    .groupBy { it.fileSize }
                    .filter { it.key > 0 && it.value.size > 1 }
                    .values
                    .flatten()
            val hashes = mutableMapOf<String, MutableList<Track>>()
            candidates.forEachIndexed { i, t ->
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val hash = attempt {
                    val digest = MessageDigest.getInstance("SHA-256")
                    context.contentResolver.openInputStream(t.uri.toUri())!!.use { stream ->
                        val bytes = ByteArray(65536)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val n = stream.read(bytes)
                            if (n < 0) break
                            digest.update(bytes, 0, n)
                        }
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                    .getOrNull()
                if (hash != null) hashes.getOrPut(hash) { mutableListOf() }.add(t)
                onProgress(i + 1, candidates.size)
            }
            hashes.values.filter { it.size > 1 }
        }

    suspend fun exportTags(track: Track, destination: Uri) =
        withContext(Dispatchers.IO) {
            require(destination != track.uri.toUri()) {
                strings[R.string.export_destination_different]
            }
            TagConfiguration.ensureInitialized()
            val details = details(track)
            val extension = details.filename.substringAfterLast('.', "mp3").lowercase()
            require(extension != "opus") { strings[R.string.writing_opus_tags_is_not_supported] }
            val temporary = File.createTempFile("lad-tags-", ".$extension", context.cacheDir)
            try {
                context.contentResolver.openInputStream(track.uri.toUri())!!.use { source ->
                    temporary.outputStream().use { source.copyTo(it) }
                }
                val audio = AudioFileIO.read(temporary)
                val tag = audio.tagOrCreateAndSetDefault
                listOf(
                        FieldKey.TITLE to track.title,
                        FieldKey.ARTIST to track.artist,
                        FieldKey.ALBUM to track.album,
                        FieldKey.YEAR to track.year.takeIf { it > 0 }?.toString().orEmpty(),
                        FieldKey.GENRE to track.genre,
                    )
                    .forEach { (key, value) ->
                        if (value.isNotBlank()) tag.setField(key, value) else tag.deleteField(key)
                    }
                track.customArtwork?.let { cover ->
                    val bytes =
                        context.contentResolver.openInputStream(cover.toUri())?.use {
                            it.readBytes()
                        }
                    if (bytes != null) {
                        val art = org.jaudiotagger.tag.images.StandardArtwork()
                        val bounds =
                            android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        art.width = bounds.outWidth.coerceAtLeast(0)
                        art.height = bounds.outHeight.coerceAtLeast(0)
                        art.binaryData = bytes
                        art.mimeType =
                            context.contentResolver.getType(cover.toUri()) ?: "image/jpeg"
                        art.pictureType = 3
                        tag.deleteArtworkField()
                        tag.setField(art)
                    }
                }
                (tag as? org.jaudiotagger.tag.wav.WavTag)?.syncTagBeforeWrite()
                audio.commit()
                val checkedTag = AudioFileIO.read(temporary).tag
                check(checkedTag.getFirst(FieldKey.TITLE) == track.title) {
                    strings[R.string.could_not_verify_the_saved_tags]
                }
                context.contentResolver.openOutputStream(destination, "wt")!!.use { out ->
                    temporary.inputStream().use { it.copyTo(out) }
                }
            } finally {
                temporary.delete()
            }
        }
}

private object TagConfiguration {
    private val initialized by lazy {
        org.jaudiotagger.tag.TagOptionSingleton.getInstance().apply {
            setAndroid(true)
            setWavOptions(org.jaudiotagger.audio.wav.WavOptions.READ_ID3_ONLY_AND_SYNC)
            setWavSaveOptions(org.jaudiotagger.audio.wav.WavSaveOptions.SAVE_BOTH_AND_SYNC)
        }
    }

    fun ensureInitialized() {
        initialized
    }
}
