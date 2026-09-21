package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.PetStats
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.minigame.wallbreak.RuleKind
import com.nebu965039.monsterraising.minigame.wallbreak.WallBreakResult
import com.nebu965039.monsterraising.minigame.wallbreak.WallDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallBreakRewardsTest {
    private fun result(score: Int, difficulty: WallDifficulty = WallDifficulty.BEGINNER) = WallBreakResult(
        difficulty, RuleKind.INK, wallsBroken = score, score = score, maxCombo = 0, mistakes = 0, nullifiedUsed = 0, cleared = false,
    )

    // --- ミス無効の回数(7.3) ---

    @Test
    fun nullifyCount_isTheSmallerOfTheStageCapAndStrengthOverOneHundred() {
        assertEquals(2, MissNullify.count(Stage.MATURE, 210.0)) // 設計書の例: 成熟期で筋力 210 → 2 回
        assertEquals(3, MissNullify.count(Stage.MATURE, 300.0))
        assertEquals(3, MissNullify.count(Stage.MATURE, 999.0)) // 上限は 3 回
        assertEquals(1, MissNullify.count(Stage.GROWTH_1, 500.0)) // 成長期Ⅰの上限は 1 回
        assertEquals(2, MissNullify.count(Stage.GROWTH_2, 500.0))
        assertEquals(1, MissNullify.count(Stage.GROWTH_2, 100.0))
    }

    @Test
    fun nullifyCount_isZeroBelowOneHundredStrength() {
        assertEquals(0, MissNullify.count(Stage.MATURE, 99.9))
        assertEquals(0, MissNullify.count(Stage.MATURE, 0.0))
        assertEquals(0, MissNullify.count(Stage.MATURE, -5.0))
    }

    @Test
    fun nullifyCount_isZeroBeforeTheGrowthStages() {
        assertEquals(0, MissNullify.count(Stage.EGG, 900.0))
        assertEquals(0, MissNullify.count(Stage.INFANT, 900.0))
    }

    @Test
    fun nullifyCount_readsThePetsStrength() {
        val pet = PetState(PetStats(100.0, 100.0, 50.0, intellect = 900.0, strength = 250.0), Stage.MATURE, 0L, 0L)
        assertEquals(2, MissNullify.forPet(pet)) // 知力ではなく筋力を見る
    }

    // --- 育成への反映(7.5) ---

    @Test
    fun strengthOnly_isAddedFromTheScore() {
        val g = WallBreakRewards.gains(result(score = 25))
        assertEquals(12.0, g.strength, 1e-9) // 25 ÷ 2 の切り捨て
        assertEquals(0.0, g.intellect, 1e-9)
    }

    @Test
    fun smallScores_giveNothingButMoodStillGiven() {
        val g = WallBreakRewards.gains(result(score = 1))
        assertEquals(0.0, g.strength, 1e-9)
        assertEquals(10.0, g.mood, 1e-9)
    }

    @Test
    fun tier_followsTheDifficulty() {
        assertEquals(RewardTier.BEGINNER, WallBreakRewards.tier(result(1, WallDifficulty.BEGINNER)))
        assertEquals(RewardTier.INTERMEDIATE, WallBreakRewards.tier(result(1, WallDifficulty.INTERMEDIATE)))
        assertEquals(RewardTier.ADVANCED, WallBreakRewards.tier(result(1, WallDifficulty.ADVANCED)))
    }

    @Test
    fun advancedFirstClear_givesTheStrengthEquipment() {
        val s = MiniGameProgress().settle(MiniGame.WALL_BREAK, 1L, RewardTier.ADVANCED, true, true, MinigameGains.NONE)
        assertEquals(1, s.progress.inventory.count(ItemType.EQUIP_STRENGTH))
    }

    @Test
    fun dailyCap_appliesToTheStrengthAxis() {
        var p = MiniGameProgress()
        val g = WallBreakRewards.gains(result(score = 50)) // +25
        repeat(3) { p = p.settle(MiniGame.WALL_BREAK, 1L, RewardTier.BEGINNER, false, false, g).progress }
        assertEquals(60.0, p.daily.strengthGained, 1e-9) // 75 だが 1 日の上限は 60
    }

    // --- 記録 ---

    @Test
    fun scoreRecords_keepTheBestScorePerDifficulty() {
        var r = ScoreRecords()
        r = r.record("BEGINNER", 20, false).records
        val lower = r.record("BEGINNER", 10, false)
        assertFalse(lower.isNewHighScore)
        assertEquals(20, lower.records.highScore("BEGINNER"))
        val higher = r.record("BEGINNER", 30, false)
        assertTrue(higher.isNewHighScore)
        assertEquals(0, r.highScore("ADVANCED"))
    }

    @Test
    fun scoreRecords_reportTheFirstClearOnce() {
        val a = ScoreRecords().record("ADVANCED", 40, true)
        assertTrue(a.isFirstClear)
        assertFalse(a.records.record("ADVANCED", 45, true).isFirstClear)
        assertFalse(ScoreRecords().record("ADVANCED", 40, false).isFirstClear)
    }

    @Test
    fun scoreRecords_codec() {
        val r = ScoreRecords().record("ADVANCED", 40, true).records
        assertEquals(r, ScoreRecordsCodec.decode(ScoreRecordsCodec.encode(r)))
        assertNull(ScoreRecordsCodec.decode("{ broken"))
    }
}
