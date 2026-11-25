@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.lad.ui

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import coil.imageLoader
import coil.request.ImageRequest
import dev.lad.R
import dev.lad.data.Track
import dev.lad.domain.CoverPalette
import dev.lad.domain.MusicCardContent
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import dev.lad.i18n.rememberText
import java.io.File
import kotlin.math.*
import kotlinx.coroutines.*

internal suspend fun renderMusicCard(
    context: Context,
    track: Track,
    quote: String,
    story: Boolean,
    accent: Int?,
    coverShape: String = "soft",
): Bitmap =
    withContext(Dispatchers.IO) {
        val height = if (story) 1920 else 1350
        val bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cover =
            (context.imageLoader
                    .execute(
                        ImageRequest.Builder(context)
                            .data(track.artwork)
                            .size(1000)
                            .allowHardware(false)
                            .build()
                    )
                    .drawable as? BitmapDrawable)
                ?.bitmap
        val pixels =
            cover?.let { source ->
                val small = Bitmap.createScaledBitmap(source, 96, 96, true)
                IntArray(96 * 96).also { values ->
                    small.getPixels(values, 0, 96, 0, 0, 96, 96)
                    if (small !== source) small.recycle()
                }
            }
        val color = accent ?: pixels?.let { CoverPalette.seed(it) } ?: 0xff717a7d.toInt()
        fun mix(a: Int, b: Int, fraction: Float): Int =
            Color.rgb(
                (Color.red(a) + (Color.red(b) - Color.red(a)) * fraction).toInt(),
                (Color.green(a) + (Color.green(b) - Color.green(a)) * fraction).toInt(),
                (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * fraction).toInt(),
            )
        val background = mix(color, Color.BLACK, .83f)
        val foreground = mix(color, Color.WHITE, .72f)
        val font = ResourcesCompat.getFont(context, R.font.google_sans)
        val strings = TextResources(AppLanguage.context(context))
        fun layout(
            value: String,
            size: Float,
            color: Int,
            max: Int,
            bold: Boolean = true,
        ): StaticLayout {
            val textPaint =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size
                    this.color = color
                    typeface = Typeface.create(font, if (bold) Typeface.BOLD else Typeface.NORMAL)
                }
            return StaticLayout.Builder.obtain(value, 0, value.length, textPaint, 840)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setMaxLines(max)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .setLineSpacing(8f, 1f)
                .build()
        }
        val titleLayout = layout(strings.metadata(track.title), 60f, Color.WHITE, 2)
        val artistLayout = layout(strings.metadata(track.artist), 36f, foreground, 2, false)
        val quoteLayout =
            quote
                .takeIf { it.isNotBlank() }
                ?.let { layout(it, if (story) 68f else 56f, Color.WHITE, if (story) 5 else 3) }
        val brandLayout = layout("Riff", 32f, foreground, 1)
        val artSize =
            if (story) {
                if (quoteLayout == null) 820f else 760f
            } else if (quoteLayout == null) 720f else 480f
        val quoteGap = if (story) 64f else 40f
        val bodyHeight =
            artSize +
                48f +
                titleLayout.height +
                16f +
                artistLayout.height +
                (quoteLayout?.let { quoteGap + it.height } ?: 0f)
        val top = 96f + ((height - 276f - bodyHeight) / 2f).coerceAtLeast(0f)
        canvas.drawColor(background)
        paint.shader =
            RadialGradient(
                540f,
                height * .32f,
                1100f,
                intArrayOf(mix(color, Color.BLACK, .28f), background),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, 1080f, height.toFloat(), paint)
        paint.shader = null
        fun shape(cx: Float, cy: Float, radius: Float, lobes: Int, amplitude: Float = .12f): Path =
            Path().apply {
                for (step in 0..240) {
                    val angle = step * PI * 2 / 240
                    val r = radius * (1 - amplitude + amplitude * cos(lobes * angle))
                    val x = cx + (cos(angle) * r).toFloat()
                    val y = cy + (sin(angle) * r).toFloat()
                    if (step == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        paint.color = (foreground and 0xffffff) or 0x24000000
        canvas.drawPath(shape(100f, height * .15f, 260f, 8), paint)
        canvas.drawPath(shape(1040f, height * .50f, 270f, 3), paint)
        canvas.drawPath(shape(100f, height * .85f, 190f, 4), paint)
        val rect = RectF((1080f - artSize) / 2, top, (1080f + artSize) / 2, top + artSize)
        val artPath = buildCoverAndroidPath(rect, coverShape, 64f)
        paint.alpha = 255
        canvas.save()
        canvas.clipPath(artPath)
        if (cover != null) {
            val side = minOf(cover.width, cover.height)
            val x = (cover.width - side) / 2
            val y = (cover.height - side) / 2
            canvas.drawBitmap(cover, Rect(x, y, x + side, y + side), rect, paint)
        } else {
            paint.color = mix(color, Color.BLACK, .2f)
            canvas.drawRect(rect, paint)
            paint.color = foreground
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            listOf(.25f, .30f, .35f).forEach {
                canvas.drawCircle(rect.centerX(), rect.centerY(), artSize * it, paint)
            }
            paint.style = Paint.Style.FILL
            canvas.drawCircle(rect.centerX(), rect.centerY(), artSize * .08f, paint)
        }
        canvas.restore()
        fun draw(layout: StaticLayout, y: Float): Float {
            canvas.save()
            canvas.translate(120f, y)
            layout.draw(canvas)
            canvas.restore()
            return y + layout.height
        }
        var y = top + artSize + 48f
        y = draw(titleLayout, y) + 16f
        y = draw(artistLayout, y)
        quoteLayout?.let { draw(it, y + quoteGap) }
        draw(brandLayout, height - 100f)
        bitmap
    }

@Composable
internal fun MusicSharePanel(
    track: Track,
    vm: LadViewModel,
    initialQuote: String = "",
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val strings = rememberText()
    val scope = rememberCoroutineScope()
    var quote by remember { mutableStateOf(initialQuote) }
    var story by remember { mutableStateOf(true) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var renderedKey by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val ready = image != null && renderedKey == (quote to story)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var quoteResolved by remember(track.id) { mutableStateOf(false) }
    val snapshot = remember(track.id) { vm.playback.state.value }
    val settings = LocalLadSettings.current
    LaunchedEffect(track.id) {
        try {
            if (initialQuote.isBlank() && snapshot.current?.id == track.id) {
                val record = vm.lyrics.load(track)
                val elapsed =
                    if (snapshot.playing)
                        ((android.os.SystemClock.elapsedRealtime() - snapshot.positionTimestampMs)
                                .coerceAtLeast(0) * snapshot.speed)
                            .toLong()
                    else 0L
                quote =
                    MusicCardContent.currentLine(
                        record?.text.orEmpty(),
                        snapshot.positionMs + elapsed,
                        vm.appearance.lyricsOffset(track.id),
                    )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            quote = initialQuote
        } finally {
            quoteResolved = true
        }
    }
    LaunchedEffect(quote, story, quoteResolved) {
        if (!quoteResolved) return@LaunchedEffect
        try {
            image =
                renderMusicCard(
                    context,
                    track,
                    quote,
                    story,
                    vm.appearance.albumAccent(track),
                    settings.coverShape,
                )
            renderedKey = quote to story
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            error = strings[R.string.card_failed]
        }
    }
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri
            ->
            val current = image
            if (uri != null && current != null)
                scope.launch {
                    busy = true
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use {
                                current.compress(Bitmap.CompressFormat.PNG, 100, it)
                            } ?: error("Output unavailable")
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        error = strings[R.string.card_failed]
                    } finally {
                        busy = false
                    }
                }
        }
    ExpressiveSheet(onClose) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(strings[R.string.share_card], style = MaterialTheme.typography.headlineSmall)
            ChoiceChips(
                if (story) "story" else "post",
                listOf("story" to "9:16", "post" to "4:5"),
            ) {
                story = it == "story"
            }
            if (image == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            image?.let {
                Image(
                    it.asImageBitmap(),
                    strings[R.string.preview],
                    Modifier.fillMaxWidth().heightIn(max = 360.dp),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    { save.launch("Riff-card.png") },
                    modifier = Modifier.weight(1f),
                    enabled = ready && !busy,
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.save])
                }
                Button(
                    {
                        val current = image ?: return@Button
                        busy = true
                        scope.launch {
                            try {
                                val file =
                                    withContext(Dispatchers.IO) {
                                        val folder =
                                            File(context.cacheDir, "recaps").apply { mkdirs() }
                                        folder
                                            .listFiles()
                                            ?.filter {
                                                it.name.startsWith("music-") &&
                                                    System.currentTimeMillis() - it.lastModified() >
                                                        86400000
                                            }
                                            ?.forEach { it.delete() }
                                        File(folder, "music-${System.currentTimeMillis()}.png")
                                            .also { file ->
                                                file.outputStream().use {
                                                    current.compress(
                                                        Bitmap.CompressFormat.PNG,
                                                        100,
                                                        it,
                                                    )
                                                }
                                            }
                                    }
                                val uri =
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.files",
                                        file,
                                    )
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND)
                                            .setType("image/png")
                                            .putExtra(Intent.EXTRA_STREAM, uri)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                        null,
                                    )
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (e: Exception) {
                                error = strings[R.string.card_failed]
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = ready && !busy,
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.share_action])
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
