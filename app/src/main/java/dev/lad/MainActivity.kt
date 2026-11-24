package dev.lad

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lad.i18n.AppLanguage
import dev.lad.ui.LadApp

internal fun Window.preferHighestRefreshRate() {
    if (Build.VERSION.SDK_INT < 30) return
    runCatching {
        val activeDisplay = context.display ?: return
        val current = activeDisplay.mode
        val best =
            activeDisplay.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                .maxByOrNull { it.refreshRate } ?: return
        if (
            attributes.preferredDisplayModeId != best.modeId ||
                attributes.preferredRefreshRate != best.refreshRate
        ) {
            attributes =
                attributes.apply {
                    preferredDisplayModeId = best.modeId
                    preferredRefreshRate = best.refreshRate
                }
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var appliedLanguage: String

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = AppLanguage.current(newBase)
        super.attachBaseContext(AppLanguage.context(newBase, appliedLanguage))
    }

    override fun onStart() {
        super.onStart()
        (application as LadApplication).playback.onActivityStarted()
    }

    override fun onStop() {
        super.onStop()
        (application as LadApplication).playback.onActivityStopped()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.sync(this)
        enableEdgeToEdge()
        window.preferHighestRefreshRate()
        setContent {
            val language by AppLanguage.selected.collectAsStateWithLifecycle()
            LaunchedEffect(language) {
                if (Build.VERSION.SDK_INT < 33 && language != appliedLanguage) recreate()
            }
            LadApp()
        }
    }
}
