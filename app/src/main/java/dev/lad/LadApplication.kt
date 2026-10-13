package dev.lad

import android.app.Application
import androidx.room.Room
import dev.lad.data.*
import dev.lad.playback.PlaybackRepository

class LadApplication : Application(), coil.ImageLoaderFactory {
    private val artwork by lazy { ArtworkRepository(this) }

    override fun newImageLoader() =
        coil.ImageLoader.Builder(this)
            .components { add(ArtworkRepository.Factory(artwork)) }
            .crossfade(false)
            .build()

    val persistenceScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )
    lateinit var database: LadDatabase
        private set

    lateinit var settings: SettingsStore
        private set

    lateinit var music: MusicRepository
        private set

    lateinit var playback: PlaybackRepository
        private set

    override fun onCreate() {
        super.onCreate()
        dev.lad.i18n.AppLanguage.sync(this)
        database =
            Room.databaseBuilder(this, LadDatabase::class.java, "lad.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        settings = SettingsStore(this)
        music = MusicRepository(this, database)
        playback = PlaybackRepository(this)
        val power = getSystemService(android.os.PowerManager::class.java)
        playback.systemSaving.value = power.isPowerSaveMode
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            object : android.content.BroadcastReceiver() {
                override fun onReceive(
                    context: android.content.Context,
                    intent: android.content.Intent,
                ) {
                    playback.systemSaving.value = power.isPowerSaveMode
                }
            },
            android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
