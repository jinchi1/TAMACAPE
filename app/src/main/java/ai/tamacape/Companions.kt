package ai.tamacape

internal data class Companion(val id: String, val name: String, val description: String,
    val art: Int, val clears: Int, val cost: Int,
    val celebrateArt: Int, val thinkingArt: Int)

internal val companions = listOf(
    Companion("mobi", "모비", "궁금한 게 많은 우리 가족의 첫 번째 친구", R.drawable.mobi_sprite, 0, 0, R.drawable.mobi_celebrate, R.drawable.mobi_thinking),
    Companion("lulu", "루루", "잘 듣고 함께 생각하는 다정한 토끼 친구", R.drawable.friend_lulu, 1, 100, R.drawable.lulu_celebrate, R.drawable.lulu_thinking),
    Companion("bori", "보리", "친구를 기다려 주는 든든한 곰 친구", R.drawable.friend_bori, 3, 200, R.drawable.bori_celebrate, R.drawable.bori_thinking),
)
internal val AdventureSave.activeCompanion: Companion
    get() = companions.firstOrNull { it.id == companion && hasCompanion(it) } ?: companions.first()
internal fun AdventureSave.hasCompanion(friend: Companion) = friend.cost == 0 || "friend:" + friend.id in owned
internal fun AdventureSave.chooseCompanion(id: String): AdventureSave {
    val friend = companions.firstOrNull { it.id == id } ?: return this
    if (hasCompanion(friend)) return copy(companion = id)
    if (progress.cleared < friend.clears || progress.coins < friend.cost) return this
    return copy(companion = id, owned = owned + ("friend:" + id),
        progress = progress.copy(coins = progress.coins - friend.cost))
}
