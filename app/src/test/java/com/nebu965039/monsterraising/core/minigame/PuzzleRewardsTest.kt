package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.PetStats
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.minigame.puzzle.Difficulty
import com.nebu965039.monsterraising.minigame.puzzle.GameStatus
import com.nebu965039.monsterraising.minigame.puzzle.PuzzleResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleRewardsTest {
    private fun result(
        score: Int,
        difficulty: Difficulty = Difficulty.BEGINNER,
        cleared: Boolean = false,
    ) = PuzzleResult(difficulty, score, linesCleared = score / 100, cleared = cleared, endReason = GameStatus.TimeUp)

    // --- 育成への反映(5.4) ---

    @Test
    fun effectiveness_isFloorOfScoreOverOneHundred_forBothAxes() {
        val g = PuzzleRewards.gains(result(score = 375))
        assertEquals(3.0, g.intellect, 1e-9)
        assertEquals(3.0, g.strength, 1e-9)
    }

    @Test
    fun scoreBelowOneHundred_givesNoEffectivenessButStillGivesMood() {
        val g = PuzzleRewards.gains(result(score = 99))
        assertEquals(0.0, g.intellect, 1e-9)
        assertEquals(0.0, g.strength, 1e-9)
        assertEquals(10.0, g.mood, 1e-9)
    }

    @Test
    fun mood_isConstantRegardlessOfScore() {
        assertEquals(PuzzleRewards.gains(result(0)).mood, PuzzleRewards.gains(result(1_500)).mood, 1e-9)
    }

    @Test
    fun petReceivesTheGains() {
        val pet = PetState(PetStats(100.0, 100.0, 40.0, intellect = 5.0, strength = 2.0), Stage.GROWTH_1, 0L, 0L)
        val after = PetSimulator.applyMinigame(pet, 0L, PuzzleRewards.gains(result(score = 800)))
        assertEquals(13.0, after.stats.intellect, 1e-9)
        assertEquals(10.0, after.stats.strength, 1e-9)
        assertEquals(50.0, after.stats.mood, 1e-9)
    }

    @Test
    fun moodIsCappedAtOneHundred() {
        val pet = PetState(PetStats(100.0, 100.0, 95.0), Stage.GROWTH_1, 0L, 0L)
        assertEquals(100.0, PetSimulator.applyMinigame(pet, 0L, PuzzleRewards.gains(result(0))).stats.mood, 1e-9)
    }

    @Test
    fun eggCannotPlay_soNothingIsApplied() {
        val egg = PetState.newEgg(0L)
        val after = PetSimulator.applyMinigame(egg, 30_000L, PuzzleRewards.gains(result(800)))
        assertEquals(egg.stats, after.stats)
    }

    @Test
    fun noGains_changeNothing() {
        val pet = PetState(PetStats(100.0, 100.0, 40.0), Stage.GROWTH_1, 0L, 0L)
        assertEquals(pet.stats, PetSimulator.applyMinigame(pet, 0L, MinigameGains.NONE).stats)
    }

    // --- 記録(5.5) ---

    @Test
    fun highScore_isUpdatedOnlyWhenBeaten() {
        val first = PuzzleRecords().record(result(400))
        assertTrue(first.isNewHighScore)
        assertEquals(400, first.records.highScore(Difficulty.BEGINNER))
        val lower = first.records.record(result(300))
        assertFalse(lower.isNewHighScore)
        assertEquals(400, lower.records.highScore(Difficulty.BEGINNER))
        val higher = lower.records.record(result(500))
        assertTrue(higher.isNewHighScore)
        assertEquals(500, higher.records.highScore(Difficulty.BEGINNER))
    }

    @Test
    fun highScores_areKeptPerDifficulty() {
        val r = PuzzleRecords()
            .record(result(400, Difficulty.BEGINNER)).records
            .record(result(900, Difficulty.INTERMEDIATE)).records
        assertEquals(400, r.highScore(Difficulty.BEGINNER))
        assertEquals(900, r.highScore(Difficulty.INTERMEDIATE))
        assertEquals(0, r.highScore(Difficulty.ADVANCED))
    }

    @Test
    fun firstClear_isReportedOnlyOnce() {
        val a = PuzzleRecords().record(result(1_600, Difficulty.ADVANCED, cleared = true))
        assertTrue(a.isFirstClear)
        assertTrue(a.records.hasCleared(Difficulty.ADVANCED))
        val b = a.records.record(result(1_700, Difficulty.ADVANCED, cleared = true))
        assertFalse(b.isFirstClear)
    }

    @Test
    fun notCleared_doesNotMarkTheDifficultyCleared() {
        val a = PuzzleRecords().record(result(200, Difficulty.BEGINNER, cleared = false))
        assertFalse(a.isFirstClear)
        assertFalse(a.records.hasCleared(Difficulty.BEGINNER))
    }

    @Test
    fun scoreZeroIsNotAHighScore() {
        assertFalse(PuzzleRecords().record(result(0)).isNewHighScore)
    }

    @Test
    fun codec_roundTripsAndRejectsGarbage() {
        val r = PuzzleRecords().record(result(1_600, Difficulty.ADVANCED, cleared = true)).records
        assertEquals(r, PuzzleRecordsCodec.decode(PuzzleRecordsCodec.encode(r)))
        assertNull(PuzzleRecordsCodec.decode("{ broken"))
        assertNull(PuzzleRecordsCodec.decode("[1,2]"))
    }
}
