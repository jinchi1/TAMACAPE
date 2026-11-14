package ai.tamacape

import org.junit.Assert.*
import org.junit.Test

class SongRewardTest {
    @Test fun sampleDoesNotEarnRewardAndGeneratedSongAwardsOnlyOnce() {
        val save = AdventureSave(progress = Progress(3, 300, 200),
            owned = setOf("lulu"), companion = "lulu",
            checkpoints = mapOf(2 to PuzzleCheckpoint(puzzle = 1)))
        assertEquals(save, save.awardFirstSong(false))
        val rewarded = save.awardFirstSong(true)
        assertEquals(320, rewarded.progress.xp)
        assertEquals(save.progress.coins, rewarded.progress.coins)
        assertEquals(save.checkpoints, rewarded.checkpoints)
        assertEquals(save.companion, rewarded.companion)
        assertTrue(rewarded.owned.containsAll(save.owned))
        assertEquals(rewarded, rewarded.awardFirstSong(true))
        assertEquals(rewarded, rewarded.awardFirstSong(false))
    }
}
