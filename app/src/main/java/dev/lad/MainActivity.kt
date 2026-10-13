package dev.lad

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lad.i18n.AppLanguage
import dev.lad.ui.LadApp

class MainActivity : ComponentActivity() {
    private lateinit var appliedLanguage: String

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = AppLanguage.current(newBase)
        super.attachBaseContext(AppLanguage.context(newBase, appliedLanguage))
    }

    override fun onStart() {
        super.onStart()
        (application as LadApplication).playback.uiVisible.value = true
    }

    override fun onStop() {
        (application as LadApplication).playback.uiVisible.value = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.sync(this)
        enableEdgeToEdge()
        setContent {
            val language by AppLanguage.selected.collectAsStateWithLifecycle()
            LaunchedEffect(language) {
                if (Build.VERSION.SDK_INT < 33 && language != appliedLanguage) recreate()
            }
            LadApp()
        }
    }
}
