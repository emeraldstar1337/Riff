@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import dev.lad.LadApplication
import dev.lad.R
import dev.lad.data.Track
import dev.lad.domain.CoverPalette
import dev.lad.i18n.rememberText
import dev.lad.statistics.*
import dev.lad.util.attempt
import java.io.File
import kotlinx.coroutines.*

@Composable
fun RecapShareButton(
    stats: MusicStats,
    slide: RecapSlide,
    label: String,
    tracks: List<Track> = emptyList(),
) {
    val strings = rememberText()
    val context = LocalContext.current
    val settings = LocalLadSettings.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    IconButton(
        onClick = {
            busy = true
            scope.launch {
                try {
                    attempt {
                            val file =
                                withContext(Dispatchers.IO) {
                                    val bitmap =
                                        Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                                    try {
                                        val c = Canvas(bitmap)
                                        val paint =
                                            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                        val font =
                                            androidx.core.content.res.ResourcesCompat.getFont(
                                                context,
                                                R.font.google_sans,
                                            )
                                        val lead = slide.ranking.firstOrNull()
                                        val artistImage =
                                            if (lead?.detail == "Artist" && settings.artistPhotos) {
                                                val repo =
                                                    (context.applicationContext as? LadApplication)
                                                        ?.artistPhotos
                                                val matchingTracks =
                                                    tracks.filter {
                                                        it.artist.contains(
                                                            lead.name,
                                                            ignoreCase = true,
                                                        )
                                                    }
                                                repo?.peek(lead.name)?.image
                                                    ?: repo?.find(lead.name, matchingTracks)?.image
                                            } else null
                                        val cover =
                                            artistImage
                                                ?: lead?.artwork
                                                ?: stats.tracks.firstOrNull()?.artwork
                                        val image =
                                            cover
                                                ?.let {
                                                    context.imageLoader
                                                        .execute(
                                                            ImageRequest.Builder(context)
                                                                .data(it)
                                                                .size(900)
                                                                .allowHardware(false)
                                                                .build()
                                                        )
                                                        .drawable as? BitmapDrawable
                                                }
                                                ?.bitmap
                                        val seedColor =
                                            image?.let { bmp ->
                                                val small =
                                                    Bitmap.createScaledBitmap(bmp, 72, 72, true)
                                                val px = IntArray(72 * 72)
                                                small.getPixels(px, 0, 72, 0, 0, 72, 72)
                                                if (small !== bmp) small.recycle()
                                                CoverPalette.seed(px)
                                            } ?: 0xFF7B5CE6.toInt()
                                        fun mix(a: Int, b: Int, f: Float): Int =
                                            Color.rgb(
                                                (Color.red(a) + (Color.red(b) - Color.red(a)) * f)
                                                    .toInt(),
                                                (Color.green(a) +
                                                        (Color.green(b) - Color.green(a)) * f)
                                                    .toInt(),
                                                (Color.blue(a) +
                                                        (Color.blue(b) - Color.blue(a)) * f)
                                                    .toInt(),
                                            )
                                        val bg = mix(seedColor, Color.BLACK, 0.88f)
                                        val cardBg = mix(seedColor, Color.BLACK, 0.68f)
                                        val secondaryCardBg = mix(seedColor, Color.BLACK, 0.78f)
                                        val accent = mix(seedColor, Color.WHITE, 0.58f)
                                        c.drawColor(bg)
                                        paint.shader =
                                            RadialGradient(
                                                540f,
                                                320f,
                                                1100f,
                                                intArrayOf(mix(seedColor, Color.BLACK, 0.52f), bg),
                                                null,
                                                Shader.TileMode.CLAMP,
                                            )
                                        c.drawRect(0f, 0f, 1080f, 1920f, paint)
                                        paint.shader = null

                                        // Clean M3 Expressive Bento Hero Container
                                        paint.color = cardBg
                                        c.drawRoundRect(
                                            RectF(64f, 180f, 1016f, 1280f),
                                            80f,
                                            80f,
                                            paint,
                                        )

                                        var contentTop = 280f
                                        if (image != null) {
                                            val artSize = 440f
                                            val artRect =
                                                RectF(
                                                    (1080f - artSize) / 2f,
                                                    250f,
                                                    (1080f + artSize) / 2f,
                                                    250f + artSize,
                                                )
                                            val clipKey = slide.shape.name.lowercase()
                                            val artPath = buildCoverAndroidPath(artRect, clipKey, 64f)
                                            c.save()
                                            c.clipPath(artPath)
                                            val side = minOf(image.width, image.height)
                                            val sx = (image.width - side) / 2
                                            val sy = (image.height - side) / 2
                                            c.drawBitmap(
                                                image,
                                                Rect(sx, sy, sx + side, sy + side),
                                                artRect,
                                                paint,
                                            )
                                            c.restore()
                                            contentTop = 760f
                                        } else {
                                            contentTop = 380f
                                        }

                                        fun text(
                                            value: String,
                                            x: Float,
                                            y: Float,
                                            maxWidth: Float,
                                            size: Float,
                                            bold: Boolean = true,
                                            maxLines: Int = 3,
                                            color: Int = Color.WHITE,
                                        ): Float {
                                            paint.color = color
                                            paint.textSize = size
                                            paint.typeface =
                                                if (bold) Typeface.create(font, Typeface.BOLD)
                                                else Typeface.create(font, Typeface.NORMAL)
                                            var cursor = y
                                            var line = ""
                                            var count = 0
                                            value.split(Regex("\\s+")).forEach { word ->
                                                val next =
                                                    if (line.isBlank()) word else "$line $word"
                                                if (
                                                    paint.measureText(next) > maxWidth &&
                                                        line.isNotBlank()
                                                ) {
                                                    if (count < maxLines) {
                                                        c.drawText(line, x, cursor, paint)
                                                        cursor += size * 1.18f
                                                        count++
                                                    }
                                                    line = word
                                                } else line = next
                                            }
                                            if (count < maxLines && line.isNotBlank()) {
                                                c.drawText(line, x, cursor, paint)
                                                cursor += size * 1.18f
                                            }
                                            return cursor
                                        }

                                        text(
                                            strings.format(R.string.recap_share_lad, label),
                                            72f,
                                            116f,
                                            936f,
                                            30f,
                                            true,
                                            1,
                                            accent,
                                        )
                                        var y =
                                            text(
                                                slide.eyebrow,
                                                124f,
                                                contentTop,
                                                832f,
                                                28f,
                                                true,
                                                1,
                                                accent,
                                            )
                                        y = text(slide.title, 124f, y + 32f, 832f, 54f, maxLines = 2)
                                        y =
                                            text(
                                                slide.value,
                                                124f,
                                                y + 44f,
                                                832f,
                                                if (slide.value.length < 12) 104f else 62f,
                                                maxLines = 2,
                                            )
                                        text(
                                            slide.caption,
                                            124f,
                                            y + 24f,
                                            832f,
                                            34f,
                                            false,
                                            2,
                                            mix(accent, Color.WHITE, 0.65f),
                                        )

                                        if (slide.ranking.isNotEmpty()) {
                                            slide.ranking.take(3).forEachIndexed { i, r ->
                                                val rowTop = 1320f + i * 136f
                                                paint.color = secondaryCardBg
                                                c.drawRoundRect(
                                                    RectF(64f, rowTop, 1016f, rowTop + 120f),
                                                    44f,
                                                    44f,
                                                    paint,
                                                )
                                                text(
                                                    strings.format(
                                                        R.string.min,
                                                        i + 1,
                                                        r.name,
                                                        r.listenedMs / 60000,
                                                    ),
                                                    112f,
                                                    rowTop + 74f,
                                                    856f,
                                                    34f,
                                                    true,
                                                    1,
                                                )
                                            }
                                        }

                                        text(
                                            strings[R.string.my_music_my_rhythm],
                                            72f,
                                            1848f,
                                            936f,
                                            28f,
                                            false,
                                            1,
                                            accent,
                                        )
                                        val directory =
                                            File(context.cacheDir, "recaps").apply { mkdirs() }
                                        val file = File(directory, "lad-recap.png")
                                        file.outputStream().use {
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                        }
                                        file
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                            val uri =
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.files",
                                    file,
                                )
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    strings[R.string.save_or_share_your_recap],
                                )
                            )
                        }
                        .onFailure { error = it.message }
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
    ) {
        Icon(Icons.Default.Share, strings[R.string.save_recap_card])
    }
    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(strings[R.string.could_not_create_the_recap_card]) },
            text = { Text(it) },
            confirmButton = {
                TextButton(
                    { error = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.ok])
                }
            },
        )
    }
}
