package ai.tamacape

internal data class Progress(val cleared: Int, val xp: Int, val coins: Int) {
    val growth: MobiGrowth get() = MobiGrowth.entries.last { xp >= it.startXp }
    val level: Int get() = growth.levelAt(xp)
    val levelStartXp: Int get() = growth.xpAt(level)
    val nextLevelXp: Int get() = growth.xpAt(level + 1)

    fun isUnlocked(stage: Int): Boolean = stage in stages.indices && stage <= cleared

    fun complete(stage: Int, puzzleCount: Int): Progress {
        if (!isUnlocked(stage) || stage != cleared || puzzleCount != stages[stage].puzzles.size) {
            return this
        }
        return copy(cleared = cleared + 1, xp = xp + 100, coins = coins + 100)
    }
}

internal enum class MobiGrowth(
    val title: String,
    val startXp: Int,
    val startLevel: Int,
    private val xpSpan: Int,
    private val levelSpan: Int,
) {
    SPROUT("새싹 모비", 0, 1, 100, 9),
    EXPLORER("탐험가 모비", 100, 10, 200, 10),
    SAFETY_EXPLORER("안전 탐험가", 300, 20, 200, 10),
    MASTER("별빛 마스터", 500, 30, 100, 1);

    fun levelAt(xp: Int): Int = startLevel + (xp - startXp) * levelSpan / xpSpan

    fun xpAt(level: Int): Int =
        startXp + ((level - startLevel) * xpSpan + levelSpan - 1) / levelSpan
}

internal fun Puzzle.accepts(selection: List<Int>): Boolean =
    if (ordered) selection == answer else selection.size == answer.size &&
        selection.toSet() == answer.toSet()

@Suppress("UNUSED_PARAMETER")
internal fun stageGrade(usedHint: Boolean, madeMistake: Boolean): Int = 3

internal data class Achievement(val title: String, val current: Int, val target: Int) {
    val earned: Boolean get() = current >= target
}

internal fun adventureAchievements(cleared: Int, stars: Int): List<Achievement> = listOf(
    Achievement("차고의 첫 열쇠", cleared.coerceAtMost(1), 1),
    Achievement("별빛 차고 탐험 완료", cleared, stages.size),
    Achievement("별빛 수집가", stars.coerceAtMost(10), 10),
)
