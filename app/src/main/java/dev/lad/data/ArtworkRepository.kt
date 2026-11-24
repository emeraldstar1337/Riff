package dev.lad.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath

fun trackArtworkUri(id: Long, source: String, modified: Long, size: Long, revision: Long): String =
    "lad-art://track/$id?source=${URLEncoder.encode(source, "UTF-8")}&modified=$modified&size=$size&revision=$revision"

class ArtworkRepository(private val context: Context) {
    private val keyLocks = ConcurrentHashMap<String, Mutex>()
    private val pruneLock = Mutex()
    private val directory = File(context.cacheDir, "track-artwork-v1")
    @Volatile private var prunedAt = 0L

    suspend fun resolve(uri: Uri): File? =
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val key =
                MessageDigest.getInstance("SHA-256")
                    .digest(uri.toString().toByteArray())
                    .joinToString("") { "%02x".format(it) }
            val file = File(directory, "$key.cover")
            val absent = File(directory, "$key.none")
            if (file.isFile) {
                file.setLastModified(System.currentTimeMillis())
                return@withContext file
            }
            if (absent.isFile) return@withContext null
            val mutex = keyLocks.getOrPut(key) { Mutex() }
            try {
                mutex.withLock {
                    if (file.isFile) {
                        file.setLastModified(System.currentTimeMillis())
                        return@withLock file
                    }
                    if (absent.isFile) return@withLock null
                    val sourceParam = uri.getQueryParameter("source") ?: return@withLock null
                    val retriever = MediaMetadataRetriever()
                    val bytes =
                        try {
                            retriever.setDataSource(context, Uri.parse(sourceParam))
                            retriever.embeddedPicture
                        } catch (_: Exception) {
                            return@withLock null
                        } finally {
                            runCatching { retriever.release() }
                        }
                    if (bytes == null) {
                        runCatching { absent.writeText("") }
                        prune(absent)
                        return@withLock null
                    }
                    if (bytes.size > 20 * 1024 * 1024) return@withLock null
                    val temporary = File(directory, "$key.tmp")
                    try {
                        temporary.writeBytes(bytes)
                        if (!temporary.renameTo(file)) {
                            temporary.delete()
                            return@withLock null
                        }
                    } catch (e: Throwable) {
                        temporary.delete()
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        return@withLock null
                    }
                    prune(file)
                    file
                }
            } finally {
                if (keyLocks.size > 256) keyLocks.remove(key, mutex)
            }
        }

    private suspend fun prune(keep: File) {
        val now = System.currentTimeMillis()
        if (now - prunedAt < 300_000) return
        pruneLock.withLock {
            val current = System.currentTimeMillis()
            if (current - prunedAt < 300_000) return@withLock
            prunedAt = current
            val files = directory.listFiles()?.sortedBy { it.lastModified() }.orEmpty()
            var total = files.sumOf { it.length() }
            for (cached in files) {
                if (cached.extension == "tmp" && current - cached.lastModified() > 60_000) {
                    cached.delete()
                    continue
                }
                if (total <= 128L * 1024 * 1024) break
                if (cached != keep) {
                    total -= cached.length()
                    cached.delete()
                }
            }
            files.filter { it.extension == "none" }.dropLast(2048).forEach { it.delete() }
        }
    }

    class Factory(private val repository: ArtworkRepository) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "lad-art") return null
            return Fetcher {
                repository.resolve(data)?.let {
                    SourceResult(
                        ImageSource(it.toOkioPath(), FileSystem.SYSTEM),
                        null,
                        DataSource.DISK,
                    )
                }
            }
        }
    }
}
