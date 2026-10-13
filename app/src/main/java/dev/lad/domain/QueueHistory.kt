package dev.lad.domain

data class QueueRevision(
    val id: Long,
    val label: String,
    val createdAt: Long,
    val snapshot: QueueSnapshot,
)

class QueueHistory {
    var revisions: List<QueueRevision> = emptyList()
        private set

    fun load(rows: List<QueueRevision>) {
        revisions = rows.takeLast(12)
    }

    fun record(label: String, snapshot: QueueSnapshot, now: Long = System.currentTimeMillis()) {
        if (snapshot.entries.isEmpty()) return
        val id = maxOf(now, (revisions.lastOrNull()?.id ?: 0) + 1)
        revisions = (revisions + QueueRevision(id, label, now, snapshot)).takeLast(12)
    }

    fun restore(id: Long): QueueSnapshot? {
        val index = revisions.indexOfFirst { it.id == id }
        if (index < 0) return null
        val snapshot = revisions[index].snapshot
        revisions = revisions.take(index)
        return snapshot
    }
}
