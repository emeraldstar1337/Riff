package dev.lad.domain

object FolderRules {
    fun normalize(path: String): String {
        val p = path.replace('\\', '/').trim().trim('/')
        return when {
            p == "storage/emulated/0" || p == "sdcard" -> "@primary"
            p.startsWith("storage/emulated/0/") ->
                "@primary/" + p.removePrefix("storage/emulated/0/")
            p.startsWith("sdcard/") -> "@primary/" + p.removePrefix("sdcard/")
            p.startsWith("storage/") -> "@" + p.removePrefix("storage/")
            else -> p
        }.trimEnd('/')
    }

    fun location(relative: String, volume: String = "external_primary"): String =
        "@" +
            (if (volume == "external_primary" || volume == "primary" || volume.isBlank()) "primary"
            else volume) +
            relative.trim('/').let { if (it.isBlank()) "" else "/$it" }

    fun fromDocumentId(id: String): String? {
        if (!id.contains(':')) return null
        val volume = id.substringBefore(':')
        if (volume.isBlank()) return null
        return location(id.substringAfter(':'), volume)
    }

    fun display(path: String, phone: String = "Phone", allFolders: String = "All folders"): String =
        normalize(path).let {
            if (it.startsWith("@primary"))
                "$phone · " + it.removePrefix("@primary").trim('/').ifBlank { allFolders }
            else if (it.startsWith('@')) "SD · " + it.removePrefix("@").replace('/', ' ') else it
        }

    fun entries(text: String): List<String> =
        text.lines().map(::normalize).filter { it.isNotEmpty() }.distinct()

    private fun matches(path: String, rule: String): Boolean {
        var folder = normalize(path).lowercase()
        val target = normalize(rule).lowercase()

        if (!target.startsWith('@') && folder.startsWith('@'))
            folder = folder.substringAfter('/', "")
        if (target.startsWith("@primary") && !folder.startsWith('@')) folder = location(folder)
        return folder == target || folder.startsWith("$target/")
    }

    fun accepts(path: String, included: String, excluded: String): Boolean {
        val allow = entries(included)
        return (allow.isEmpty() || allow.any { matches(path, it) }) &&
            entries(excluded).none { matches(path, it) }
    }

    fun folder(path: String): String =
        if (path.startsWith('@') || path.endsWith('/')) path
        else if (path.startsWith('/')) path.substringBeforeLast('/') else path
}
