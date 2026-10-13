package dev.lad.ui

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.domain.FolderRules
import dev.lad.i18n.rememberText
import dev.lad.util.attempt

@Composable
fun FolderPicker(
    allow: Boolean,
    initial: String,
    folders: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val strings = rememberText()
    var selected by remember { mutableStateOf(FolderRules.entries(initial).toSet()) }
    var custom by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val path =
                    if (uri.authority == "com.android.externalstorage.documents")
                        attempt {
                            FolderRules.fromDocumentId(DocumentsContract.getTreeDocumentId(uri))
                        }
                            .getOrNull()
                    else null
                if (path == null) error = strings[R.string.choose_a_folder_on_your_phone]
                else {
                    attempt {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    selected = selected + path
                    error = null
                }
            }
        }
    val paths = remember(folders, selected) { (folders + selected).distinct().sorted() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (allow) strings[R.string.music_folders] else strings[R.string.exclude_folders])
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (allow) strings[R.string.choose_one_or_more_folders_an]
                    else strings[R.string.hide_music_from_these_folders_and]
                )
                FilledTonalButton({ picker.launch(null) }, Modifier.fillMaxWidth()) {
                    Text(strings[R.string.choose_in_file_picker])
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { selected = emptySet() }) {
                    Text(strings[R.string.clear_selection])
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(paths, key = { it }) { path ->
                        val toggle = {
                            selected = if (path in selected) selected - path else selected + path
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = toggle),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(path in selected, { toggle() })
                            Text(strings.folder(path), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (paths.isEmpty())
                        item { Text(strings[R.string.folders_appear_after_you_allow_access]) }
                }
                OutlinedTextField(
                    custom,
                    { custom = it },
                    label = { Text(strings[R.string.path_for_example_music_pharaoh]) },
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        val path = FolderRules.normalize(custom)
                        if (path.isNotBlank()) {
                            selected = selected + path
                            custom = ""
                        }
                    },
                    enabled = custom.isNotBlank(),
                ) {
                    Text(strings[R.string.add_path])
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val extra = FolderRules.normalize(custom)
                    onSave(
                        (selected + listOfNotNull(extra.takeIf { it.isNotBlank() }))
                            .sorted()
                            .joinToString("\n")
                    )
                }
            ) {
                Text(strings[R.string.apply])
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings[R.string.cancel]) } },
    )
}
