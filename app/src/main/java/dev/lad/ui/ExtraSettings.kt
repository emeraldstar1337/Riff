@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.i18n.rememberText
import dev.lad.util.attempt
import dev.lad.widget.RiffArtworkWidget
import dev.lad.widget.RiffWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FontLicense() {
    val context = LocalContext.current
    val strings = rememberText()
    var open by remember { mutableStateOf(false) }
    SettingsAction(
        strings[R.string.font_license],
        "SIL Open Font License 1.1",
        Icons.Default.TextFields,
    ) {
        open = true
    }
    if (open) {
        val license by
            produceState<String?>(initialValue = null, context) {
                value =
                    withContext(Dispatchers.IO) {
                        attempt {
                                context.assets
                                    .open("licenses/GoogleSans-OFL.txt")
                                    .bufferedReader()
                                    .use { it.readText() }
                            }
                            .getOrDefault("SIL Open Font License 1.1")
                    }
            }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Google Sans") },
            text = {
                val text = license
                if (text == null) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        text,
                        Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    { open = false },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.close])
                }
            },
        )
    }
}

@Composable
internal fun WidgetAddButtons() {
    val context = LocalContext.current
    val strings = rememberText()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
                RiffWidget::class.java to R.string.widget_compact,
                RiffArtworkWidget::class.java to R.string.widget_artwork,
            )
            .forEach { (provider, title) ->
                SettingsAction(
                    strings[title],
                    strings[R.string.add_widget],
                    Icons.Default.Widgets,
                ) {
                    val manager = AppWidgetManager.getInstance(context)
                    if (manager.isRequestPinAppWidgetSupported)
                        manager.requestPinAppWidget(ComponentName(context, provider), null, null)
                    else
                        Toast.makeText(
                                context,
                                strings[R.string.widget_not_supported],
                                Toast.LENGTH_LONG,
                            )
                            .show()
                }
            }
    }
}
