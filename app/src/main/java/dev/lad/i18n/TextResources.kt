package dev.lad.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lad.R
import dev.lad.domain.FolderRules

@Immutable
class TextResources(private val context: Context) : TextProvider {
    override fun format(@StringRes id: Int, vararg arguments: Any?): String =
        if (arguments.isEmpty()) context.getString(id)
        else context.getString(id, *arguments.map { it ?: "—" }.toTypedArray())

    fun label(value: String): String = aliases()[value]?.let { this[it] } ?: value

    fun metadata(value: String): String =
        aliases()[value]?.takeIf { it in metadataIds }?.let { this[it] } ?: value

    fun folder(path: String): String =
        FolderRules.display(
            path,
            phone = this[R.string.phone].substringBefore(" ·"),
            allFolders = this[R.string.all_folders],
        )

    private fun aliases(): Map<String, Int> =
        synchronized(aliasIds) {
            aliasCache
                ?: run {
                    val ru = AppLanguage.context(context.applicationContext, "ru")
                    val en = AppLanguage.context(context.applicationContext, "en")
                    buildMap {
                        aliasIds.forEach { id ->
                            put(ru.getString(id), id)
                            put(en.getString(id), id)
                        }
                    }
                        .also { aliasCache = it }
                }
        }

    companion object {
        @Volatile private var aliasCache: Map<String, Int>? = null
        private val aliasIds =
            intArrayOf(
                R.string.quiet,
                R.string.soft,
                R.string.halo,
                R.string.atmosphere,
                R.string.balanced,
                R.string.stage,
                R.string.reading,
                R.string.compact,
                R.string.manual,
                R.string.untitled,
                R.string.unknown_artist,
                R.string.unknown_album,
                R.string.flat,
                R.string.bass,
                R.string.vocals,
                R.string.gentle,
                R.string.all_tracks,
                R.string.tracks_can_only_be_reordered_within,
                R.string.phone_speaker,
                R.string.remove_track,
                R.string.reorder_tracks,
                R.string.shuffle,
                R.string.repeat_mode,
                R.string.manual_selection,
                R.string.new_queue,
                R.string.month,
                R.string.text_3_months,
                R.string.text_6_months,
                R.string.year,
                R.string.all_time,
                R.string.winter,
                R.string.spring,
                R.string.summer,
                R.string.autumn,
                R.string.artist,
                R.string.not_specified,
                R.string.genre,
                R.string.favourites,
                R.string.recently_added,
                R.string.lrclib_synced,
            )
        private val metadataIds =
            setOf(R.string.untitled, R.string.unknown_artist, R.string.unknown_album)
    }
}

@Composable
fun rememberText(): TextResources {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val language by AppLanguage.selected.collectAsStateWithLifecycle()
    return remember(context, configuration, language) {
        TextResources(AppLanguage.context(context, language))
    }
}
