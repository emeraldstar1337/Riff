package dev.lad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lad.R
import dev.lad.data.Track
import dev.lad.i18n.rememberText
import dev.lad.util.attempt
import kotlinx.coroutines.launch

@Composable
fun DuplicatesScreen(tracks: List<Track>, vm: LadViewModel) {
    val strings = rememberText()
    val scope = rememberCoroutineScope()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    var groups by remember { mutableStateOf<List<List<Track>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BitsTitle(strings[R.string.keep_your_favourites]) }
        item { Text(strings[R.string.find_identical_files_using_sha_256]) }
        item {
            Button(
                {
                    busy = true
                    done = false
                    scope.launch {
                        attempt { vm.mediaTools.duplicates(tracks) { a, b -> progress = a to b } }
                            .onSuccess {
                                groups = it
                                done = true
                            }
                            .onFailure { error = it.message }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (busy) strings.format(R.string.comparing, progress.first, progress.second)
                    else strings[R.string.find_duplicates]
                )
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (done && groups.isEmpty()) item { Text(strings[R.string.no_identical_files_found]) }
        groups.forEachIndexed { i, group ->
            item {
                SectionTitle(
                    strings.format(R.string.copies, i + 1),
                    strings.format(R.string.identical_files, group.size),
                )
            }
            items(group, key = { "dup${it.id}" }) { t ->
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            strings.metadata(t.title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${t.artist} · ${strings.folder(dev.lad.domain.FolderRules.folder(t.path))}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton({
                            vm.hide(t)
                            groups =
                                groups
                                    .map { rows -> rows.filter { it.id != t.id } }
                                    .filter { it.isNotEmpty() }
                        }) {
                            Text(strings[R.string.hide_this_copy])
                        }
                    }
                }
            }
        }
        if (hidden.isNotEmpty())
            item {
                SectionTitle(
                    strings[R.string.hidden_files],
                    strings[R.string.audio_files_stay_on_your_device],
                )
            }
        items(hidden, key = { "hidden${it.trackId}" }) { edit ->
            ListItem(
                headlineContent = { Text(edit.title) },
                supportingContent = { Text(edit.artist) },
                trailingContent = {
                    TextButton({ vm.unhide(edit.trackId) }) { Text(strings[R.string.restore]) }
                },
            )
        }
    }
}
