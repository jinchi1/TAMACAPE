package ai.tamacape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    @Test fun familyAiOnlyAcceptsExplicitHttpsEndpoint() {
        assertTrue(validFamilyEndpoint("https://family.example/v1/family"))
        assertFalse(validFamilyEndpoint("http://family.example/v1/family"))
        assertFalse(validFamilyEndpoint("https://secret@family.example/v1/family"))
        assertFalse(validFamilyEndpoint("https://family.example/v1/family?token=secret"))
        assertFalse(validFamilyEndpoint("https://family.example/"))
    }

    @Test fun familyAiRequestIsBoundedAndUsesKnownContent() {
        assertTrue(FamilyAiRequest("coach", 0, 0, "mobi", "기다려요").valid())
        assertTrue(FamilyAiRequest("song", 0, 0, "lulu", "").valid())
        assertFalse(FamilyAiRequest("song", 0, 0, "lulu", "임의 지시").valid())
        assertFalse(FamilyAiRequest("coach", 0, 0, "mobi", "").valid())
        assertFalse(FamilyAiRequest("coach", 8, 0, "mobi", "기다려요").valid())
        assertFalse(FamilyAiRequest("coach", 0, 0, "unknown", "기다려요").valid())
        assertFalse(FamilyAiRequest("coach", 0, 0, "mobi", "가".repeat(301)).valid())
    }

    @Test fun speechNeedsUnambiguousOptionAndNeverGuesses() {
        val options = listOf("멈추고 기다리기", "주변 살피기", "안전벨트 확인")
        assertEquals(0, spokenOption("1번", options))
        assertEquals(1, spokenOption("두 번째", options))
        assertEquals(2, spokenOption("안전벨트 확인!", options))
        assertEquals(null, spokenOption("아마 멈추는 걸까요", options))
        assertEquals(null, spokenOption("3번", options.take(2)))
        assertEquals(null, spokenOption("", options))
    }

    @Test fun companionsRequireProgressAndChargeOnlyOnce() {
        val initial = AdventureSave()
        assertEquals(initial, initial.chooseCompanion("lulu"))
        val earned = initial.copy(progress = Progress(1, 100, 100))
        val chosen = earned.chooseCompanion("lulu")
        assertEquals("lulu", chosen.activeCompanion.id)
        assertEquals(0, chosen.progress.coins)
        assertEquals(chosen, chosen.chooseCompanion("lulu"))
        assertEquals("mobi", chosen.chooseCompanion("mobi").activeCompanion.id)
        assertEquals(chosen, chosen.chooseCompanion("bori"))
    }

    @Test fun fogPuzzleNeedsBothSlowingAndDistance() {
        val puzzle = stages[1].puzzles[0]
        assertTrue(puzzle.weatherScene)
        assertFalse(puzzle.accepts(emptyList()))
        assertFalse(puzzle.accepts(listOf(0)))
        assertFalse(puzzle.accepts(listOf(1)))
        assertTrue(puzzle.accepts(listOf(1, 0)))
    }

    @Test fun replayPreservesRewardsAndImprovesOnlyBestGrade() {
        var save = AdventureSave(progress = Progress(1, 100, 50),
            owned = setOf("안전모"), costume = "안전모",
            checkpoints = mapOf(0 to PuzzleCheckpoint(puzzle = 2, stars = 1)))
        save = save.restartStage(0)
        assertEquals(0, save.checkpoint(0).puzzle)
        assertEquals(1, save.starsFor(0))
        repeat(stages[0].puzzles.size) {
            save = save.withCheckpoint(0, save.checkpoint(0).copy(explained = true)).advancePuzzle(0)
        }
        assertEquals(3, save.starsFor(0))
        assertEquals(Progress(1, 100, 50), save.progress)
        assertEquals(setOf("안전모"), save.owned)
        assertEquals(save, save.advancePuzzle(0))
        save = save.restartStage(0)
        repeat(stages[0].puzzles.size) {
            save = save.withCheckpoint(0, save.checkpoint(0).copy(explained = true, madeMistake = true)).advancePuzzle(0)
        }
        assertEquals(3, save.starsFor(0))
        assertEquals(Progress(1, 100, 50), save.progress)
    }

    @Test fun lockedStageCannotBeRestartedAndUnansweredPuzzleCannotAdvance() {
        val save = AdventureSave()
        assertTrue(runCatching { save.restartStage(1) }.isFailure)
        assertEquals(save, save.advancePuzzle(0))
    }

    @Test fun completionUnlocksEachStageAndAwardsOnlyOnce() {
        var progress = Progress(0, 0, 0)
        stages.forEachIndexed { index, stage ->
            assertTrue(progress.isUnlocked(index))
            assertFalse(progress.isUnlocked(index + 1))
            assertEquals(progress, progress.complete(index + 1, stage.puzzles.size))
            assertEquals(progress, progress.complete(index, stage.puzzles.lastIndex))
            val updated = progress.complete(index, stage.puzzles.size)
            assertEquals(updated, updated.complete(index, stage.puzzles.size))
            progress = updated
        }
        assertEquals(Progress(5, 500, 500), progress)
        assertEquals(30, progress.level)
    }

    @Test fun growthThresholdsFitTheFirstWorld() {
        val thresholds = mapOf(0 to 1, 99 to 9, 100 to 10, 299 to 19, 300 to 20, 499 to 29, 500 to 30)
        for ((xp, expectedLevel) in thresholds) {
            val progress = Progress(0, xp, 0)
            assertEquals(expectedLevel, progress.level)
            assertTrue(progress.levelStartXp <= xp)
            assertTrue(progress.nextLevelXp > xp)
        }
    }

    @Test fun familyLearningDoesNotPenalizeHintsOrMistakes() {
        assertEquals(3, stageGrade(usedHint = false, madeMistake = false))
        assertEquals(3, stageGrade(usedHint = true, madeMistake = false))
        assertEquals(3, stageGrade(usedHint = false, madeMistake = true))
        assertEquals(3, stageGrade(usedHint = true, madeMistake = true))
    }

    @Test fun orderedPuzzleRequiresExactSequence() {
        val puzzle = stages.first().puzzles.first()
        assertTrue(puzzle.accepts(listOf(1, 2, 0)))
        assertFalse(puzzle.accepts(listOf(2, 1, 0)))
    }

    @Test fun signalPracticeRequiresRightSignalAndCancellation() {
        val initial = SignalPractice()
        assertEquals(initial, initial.turn())
        val left = initial.select(TurnSignal.LEFT)
        assertEquals(left, left.turn())
        val turned = left.select(TurnSignal.RIGHT).turn()
        assertEquals(SignalStep.TURNED, turned.step)
        assertEquals(SignalStep.TURNED, turned.select(TurnSignal.LEFT).step)
        val complete = turned.select(TurnSignal.OFF)
        assertEquals(SignalStep.COMPLETE, complete.step)
        assertEquals(TurnSignal.OFF, complete.signal)
    }

    @Test fun gameplayRequiresKnownUnrestrictedPlatformState() {
        assertFalse(ParkingAccess.fromPlatform(null).allowsGame)
        assertFalse(ParkingAccess.fromPlatform(true).allowsGame)
        assertTrue(ParkingAccess.fromPlatform(false).allowsGame)
    }

    @Test fun allPuzzlesHaveLearningReferences() {
        assertEquals(stages.size, learningReferences.size)
        stages.zip(learningReferences).forEach { (stage, references) ->
            assertEquals(stage.puzzles.size, references.size)
            assertTrue(references.all { entries -> entries.isNotEmpty() && entries.all { it.url.startsWith("https://") } })
        }
    }

    @Test fun multiSelectAcceptsEitherOrderAndRejectsDuplicates() {
        val puzzle = stages[2].puzzles[1]
        assertTrue(puzzle.alleyScene)
        assertTrue(puzzle.accepts(listOf(2, 0)))
        assertFalse(puzzle.accepts(listOf(2, 0, 2)))
        assertFalse(puzzle.accepts(listOf(2)))
    }
}
