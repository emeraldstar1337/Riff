package dev.lad.domain

import kotlin.random.Random

enum class RepeatMode {
    OFF,
    ONE,
    QUEUE,
}

data class QueueEntry(val trackId: Long, val cycle: Int, val manual: Boolean = false)

data class QueueSnapshot(
    val source: List<Long> = emptyList(),
    val entries: List<QueueEntry> = emptyList(),
    val index: Int = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.QUEUE,
    val context: String = "All tracks",
    val playbackMs: Long = 0,
) {
    val current: QueueEntry?
        get() = entries.getOrNull(index)

    val currentCycle: List<QueueEntry>
        get() = entries.filter { it.cycle == current?.cycle }

    val position: Int
        get() = entries.take(index + 1).count { it.cycle == current?.cycle }

    val size: Int
        get() = currentCycle.size
}

class QueueManager(private val random: Random = Random.Default) {
    var state = QueueSnapshot()
        private set

    fun start(
        source: List<Long>,
        selected: Long,
        shuffle: Boolean,
        context: String = "All tracks",
    ) {
        val ids = source.distinct()
        require(selected in ids)
        val order = if (shuffle) listOf(selected) + (ids - selected).shuffled(random) else ids
        state =
            QueueSnapshot(
                ids,
                order.map { QueueEntry(it, 0) },
                if (shuffle) 0 else order.indexOf(selected),
                shuffle,
                state.repeat,
                context,
            )
    }

    fun restore(snapshot: QueueSnapshot) {
        require(snapshot.entries.isEmpty() || snapshot.index in snapshot.entries.indices)
        state =
            snapshot.copy(
                source = snapshot.source.distinct(),
                entries =
                    snapshot.entries.filterIndexed { i, e ->
                        i <= snapshot.index ||
                            snapshot.current?.manual != true ||
                            e.trackId != snapshot.current?.trackId
                    },
                playbackMs = snapshot.playbackMs.coerceAtLeast(0),
            )
    }

    fun setIndex(index: Int) {
        require(index in state.entries.indices)
        state = state.copy(index = index, playbackMs = 0)
    }

    fun setRepeat(mode: RepeatMode) {
        state = state.copy(repeat = mode)
    }

    fun savePosition(ms: Long) {
        state = state.copy(playbackMs = ms.coerceAtLeast(0))
    }

    fun insertNow(id: Long): Int {
        if (state.current == null) {
            start(listOf(id), id, state.shuffle)
            return 0
        }
        val at = state.index + 1
        val entries =
            state.entries
                .filterIndexed { i, e -> i <= state.index || e.trackId != id }
                .toMutableList()
        entries.add(at, QueueEntry(id, state.current!!.cycle, manual = true))
        state = state.copy(entries = entries, index = at, playbackMs = 0)
        return at
    }

    fun appendCycleIfNeeded(): List<QueueEntry> {
        val current = state.current ?: return emptyList()
        if (state.repeat != RepeatMode.QUEUE || state.source.isEmpty()) return emptyList()
        if (state.entries.drop(state.index + 1).any { it.cycle > current.cycle }) return emptyList()

        if (state.entries.drop(state.index + 1).any { it.cycle == current.cycle })
            return emptyList()
        val last = state.entries.last().trackId
        val order =
            if (state.shuffle)
                state.source.shuffled(random).toMutableList().apply {
                    if (size > 1 && first() == last) {
                        val swap = 1 + random.nextInt(size - 1)
                        val first = this[0]
                        this[0] = this[swap]
                        this[swap] = first
                    }
                }
            else state.source
        val next = order.map { QueueEntry(it, current.cycle + 1) }
        state = state.copy(entries = state.entries + next)
        return next
    }

    fun next(): Long? {
        if (state.repeat == RepeatMode.ONE) return state.current?.trackId
        appendCycleIfNeeded()
        if (state.index + 1 >= state.entries.size) return null
        setIndex(state.index + 1)
        return state.current?.trackId
    }

    fun previous(): Long? {
        if (state.index > 0) setIndex(state.index - 1)
        return state.current?.trackId
    }

    fun toggleShuffle(enabled: Boolean) {
        val current =
            state.current
                ?: run {
                    state = state.copy(shuffle = enabled)
                    return
                }
        val past = state.entries.take(state.index + 1)
        val future = state.entries.drop(state.index + 1).filter { it.cycle == current.cycle }
        val sourceOrder = state.source.withIndex().associate { it.value to it.index }
        val ordered =
            if (enabled) future.shuffled(random)
            else future.sortedBy { sourceOrder[it.trackId] ?: Int.MAX_VALUE }
        state = state.copy(entries = past + ordered, shuffle = enabled)
    }

    fun removeUpcoming(index: Int) {
        require(index > state.index && index in state.entries.indices)
        state = state.copy(entries = state.entries.filterIndexed { i, _ -> i != index })
    }

    fun moveUpcoming(from: Int, to: Int) {
        require(
            from > state.index &&
                to > state.index &&
                from in state.entries.indices &&
                to in state.entries.indices
        )
        require(state.entries[from].cycle == state.entries[to].cycle) {
            "Tracks can only be reordered within the current cycle"
        }
        val entries = state.entries.toMutableList()
        entries.add(to, entries.removeAt(from))
        state = state.copy(entries = entries)
    }

    fun removePlayedManualEntries(): List<Int> {
        val removed = state.entries.indices.filter { it < state.index && state.entries[it].manual }
        if (removed.isNotEmpty())
            state =
                state.copy(
                    entries = state.entries.filterIndexed { i, _ -> i !in removed },
                    index = state.index - removed.size,
                )
        return removed
    }

    fun trimHistory(): Int {
        val current = state.current ?: return 0
        val remove = state.entries.indexOfFirst { it.cycle >= current.cycle - 1 }.coerceAtLeast(0)
        if (remove > 0)
            state = state.copy(entries = state.entries.drop(remove), index = state.index - remove)
        return remove
    }
}
