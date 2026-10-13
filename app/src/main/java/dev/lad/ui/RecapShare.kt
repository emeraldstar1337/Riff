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
import dev.lad.R
import dev.lad.i18n.rememberText
import dev.lad.statistics.*
import dev.lad.util.attempt
import java.io.File
import kotlinx.coroutines.*

@Composable
fun RecapShareButton(stats: MusicStats, slide: RecapSlide, label: String) {
    val strings = rememberText()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    IconButton(
        onClick = {
            busy = true
            scope.launch {
                attempt {
                    val file =
                        withContext(Dispatchers.IO) {
                            val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                            val c = Canvas(bitmap)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                            c.drawColor(android.graphics.Color.rgb(14, 17, 25))
                            val cover =
                                slide.ranking.firstOrNull()?.artwork
                                    ?: stats.tracks.firstOrNull()?.artwork
                            val image =
                                cover
                                    ?.let {
                                        context.imageLoader
                                            .execute(
                                                ImageRequest.Builder(context)
                                                    .data(it)
                                                    .size(800)
                                                    .allowHardware(false)
                                                    .build()
                                            )
                                            .drawable as? BitmapDrawable
                                    }
                                    ?.bitmap
                            if (image != null) {
                                paint.alpha = 90
                                c.drawBitmap(image, null, Rect(0, 0, 1080, 1000), paint)
                                paint.alpha = 255
                                paint.shader =
                                    LinearGradient(
                                        0f,
                                        0f,
                                        0f,
                                        1100f,
                                        intArrayOf(0x10101119, 0xFF101119.toInt()),
                                        null,
                                        Shader.TileMode.CLAMP,
                                    )
                                c.drawRect(0f, 0f, 1080f, 1100f, paint)
                                paint.shader = null
                            }
                            paint.color = android.graphics.Color.WHITE
                            fun text(
                                value: String,
                                y: Float,
                                size: Float,
                                bold: Boolean = true,
                                maxLines: Int = 4,
                            ): Float {
                                paint.textSize = size
                                paint.typeface =
                                    if (bold) Typeface.create("sans-serif", Typeface.BOLD)
                                    else Typeface.create("sans-serif", Typeface.NORMAL)
                                var cursor = y
                                var line = ""
                                var count = 0
                                value.split(Regex("\\s+")).forEach { word ->
                                    val next = if (line.isBlank()) word else "$line $word"
                                    if (paint.measureText(next) > 936 && line.isNotBlank()) {
                                        if (count < maxLines) {
                                            c.drawText(line, 72f, cursor, paint)
                                            cursor += size * 1.2f
                                            count++
                                        }
                                        line = word
                                    } else line = next
                                }
                                if (count < maxLines) {
                                    c.drawText(line, 72f, cursor, paint)
                                    cursor += size * 1.2f
                                }
                                return cursor
                            }
                            text(strings.format(R.string.recap_share_lad, label), 110f, 32f)
                            text(slide.eyebrow, 680f, 32f)
                            val y = text(slide.title, 780f, 76f, maxLines = 3)
                            val bottom =
                                text(
                                    slide.value,
                                    y + 70,
                                    if (slide.value.length < 12) 128f else 72f,
                                    maxLines = 4,
                                )
                            text(slide.caption, bottom + 50, 38f, false, 3)
                            slide.ranking.take(3).forEachIndexed { i, r ->
                                text(
                                    strings.format(
                                        R.string.min,
                                        i + 1,
                                        r.name,
                                        r.listenedMs / 60000,
                                    ),
                                    1450f + i * 100,
                                    36f,
                                    true,
                                    2,
                                )
                            }
                            text(strings[R.string.my_music_my_rhythm], 1840f, 30f, false)
                            val directory = File(context.cacheDir, "recaps").apply { mkdirs() }
                            val file = File(directory, "lad-recap.png")
                            file.outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            bitmap.recycle()
                            file
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
                        Intent.createChooser(intent, strings[R.string.save_or_share_your_recap])
                    )
                }
                    .onFailure { error = it.message }
                busy = false
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
            confirmButton = { TextButton({ error = null }) { Text(strings[R.string.ok]) } },
        )
    }
}
