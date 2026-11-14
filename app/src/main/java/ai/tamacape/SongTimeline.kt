package ai.tamacape

/** Estimated guide only: generated audio currently has no word/line timestamps. */
internal fun estimatedLyricLine(lyrics: String, position: Long, duration: Long): Int {
    if (duration <= 0 || position >= duration) return -1
    val sections = lyrics.split("\n\n").map { it.lines().filter(String::isNotBlank) }.filter { it.isNotEmpty() }
    if (sections.isEmpty()) return -1
    val section = if (sections.size > 1 && position >= 15000) 1 else 0
    val start = if (section == 0) 0L else 15000L
    val length = if (section == 0 && sections.size > 1) minOf(15000L, duration) else (duration - start).coerceAtLeast(1)
    val local = ((position.coerceAtLeast(start) - start) * sections[section].size / length).toInt()
        .coerceIn(0, sections[section].lastIndex)
    return sections.take(section).sumOf { it.size } + local
}

internal fun songTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
