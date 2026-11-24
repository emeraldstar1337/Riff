package dev.lad.ui.state

import dev.lad.data.Listen
import dev.lad.data.MusicDao
import dev.lad.data.PlaybackRun
import dev.lad.playback.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatisticsStateHolder(
    dao: MusicDao,
    private val playback: PlaybackRepository,
    scope: CoroutineScope,
) {
    val history: StateFlow<List<Listen>> =
        dao.listens().stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runs: StateFlow<List<PlaybackRun>> =
        dao.runs().stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearStatistics() {
        playback.command("clearStats")
    }
}
