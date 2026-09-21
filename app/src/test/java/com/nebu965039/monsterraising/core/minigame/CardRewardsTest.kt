package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.PetStats
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.minigame.card.MatchOutcome
import com.nebu965039.monsterraising.minigame.card.MatchResult
import com.nebu965039.monsterraising.minigame.card.MonsterCard
import com.nebu965039.monsterraising.minigame.card.NpcLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRewardsTest {
    private fun result(outcome: MatchOutcome, level: NpcLevel = NpcLevel.BEGINNER) =
        MatchResult(level, outcome, playerCoins = 6, npcCoins = 4, roundsPlayed = 5, monsterCard = null, monsterCardUsed = false)

    // --- 育成への反映(6.5) ---

    @Test
    fun win_addsIntellectOnly_scaledByTheOpponent() {
        val b = CardRewards.gains(result(MatchOutcome.WIN, NpcLevel.BEGINNER))
        val a = CardRewards.gains(result(MatchOutcome.WIN, NpcLevel.ADVANCED))
        assertEquals(8.0, b.intellect, 1e-9)
        assertEquals(16.0, a.intellect, 1e-9)
        assertEquals(0.0, b.strength, 1e-9) // 筋力系は加算しない
    }

    @Test
    fun loss_addsNoEffectiveness() {
        assertEquals(0.0, CardRewards.gains(result(MatchOutcome.LOSE)).intellect, 1e-9)
    }

    @Test
    fun invalidMatch_addsAVerySmallAmount() {
        val draw = CardRewards.gains(result(MatchOutcome.DRAW)).intellect
        val win = CardRewards.gains(result(MatchOutcome.WIN)).intellect
        assertTrue(draw > 0.0)
        assertTrue(draw < win / 4)
    }

    @Test
    fun moodIsGivenForPlayingRegardlessOfTheOutcome() {
        for (o in MatchOutcome.entries) assertEquals(10.0, CardRewards.gains(result(o)).mood, 1e-9)
    }

    @Test
    fun clear_meansBeatingTheOpponent() {
        assertTrue(CardRewards.isClear(result(MatchOutcome.WIN)))
        assertFalse(CardRewards.isClear(result(MatchOutcome.LOSE)))
        assertFalse(CardRewards.isClear(result(MatchOutcome.DRAW)))
        assertEquals(RewardTier.ADVANCED, CardRewards.tier(NpcLevel.ADVANCED))
    }

    // --- 記録(6.5・6.6) ---

    @Test
    fun streak_growsOnWin_resetsOnLoss_andIgnoresInvalidMatches() {
        var r = CardRecords()
        r = r.record(result(MatchOutcome.WIN)).records
        r = r.record(result(MatchOutcome.WIN)).records
        assertEquals(2, r.streak)
        r = r.record(result(MatchOutcome.DRAW)).records
        assertEquals(2, r.streak) // 無効試合は影響しない
        r = r.record(result(MatchOutcome.LOSE)).records
        assertEquals(0, r.streak)
        assertEquals(2, r.bestStreak)
    }

    @Test
    fun opponentsUnlockOneAfterAnother() {
        var r = CardRecords()
        assertTrue(r.isUnlocked(NpcLevel.BEGINNER))
        assertFalse(r.isUnlocked(NpcLevel.INTERMEDIATE))
        assertFalse(r.isUnlocked(NpcLevel.ADVANCED))

        val u1 = r.record(result(MatchOutcome.WIN, NpcLevel.BEGINNER))
        assertEquals(NpcLevel.INTERMEDIATE, u1.newlyUnlocked)
        r = u1.records
        assertTrue(r.isUnlocked(NpcLevel.INTERMEDIATE))
        assertFalse(r.isUnlocked(NpcLevel.ADVANCED))

        val u2 = r.record(result(MatchOutcome.WIN, NpcLevel.INTERMEDIATE))
        assertEquals(NpcLevel.ADVANCED, u2.newlyUnlocked)
        assertTrue(u2.records.isUnlocked(NpcLevel.ADVANCED))
    }

    @Test
    fun losingOrDrawing_doesNotUnlock() {
        val r = CardRecords()
        assertNull(r.record(result(MatchOutcome.LOSE, NpcLevel.BEGINNER)).newlyUnlocked)
        assertFalse(r.record(result(MatchOutcome.DRAW, NpcLevel.BEGINNER)).records.isUnlocked(NpcLevel.INTERMEDIATE))
    }

    @Test
    fun firstClear_isReportedOncePerLevel() {
        val a = CardRecords().record(result(MatchOutcome.WIN, NpcLevel.ADVANCED))
        assertTrue(a.isFirstClear)
        assertFalse(a.records.record(result(MatchOutcome.WIN, NpcLevel.ADVANCED)).isFirstClear)
        assertFalse(CardRecords().record(result(MatchOutcome.LOSE, NpcLevel.ADVANCED)).isFirstClear)
    }

    @Test
    fun winningAnAlreadyClearedLevel_unlocksNothingNew() {
        val r = CardRecords().record(result(MatchOutcome.WIN, NpcLevel.BEGINNER)).records
        assertNull(r.record(result(MatchOutcome.WIN, NpcLevel.BEGINNER)).newlyUnlocked)
    }

    @Test
    fun codec_roundTripsAndRejectsGarbage() {
        val r = CardRecords().record(result(MatchOutcome.WIN)).records
        assertEquals(r, CardRecordsCodec.decode(CardRecordsCodec.encode(r)))
        assertNull(CardRecordsCodec.decode("{ broken"))
    }

    // --- モンスターカード(6.4) ---

    private fun pet(stage: Stage, intellect: Double = 0.0, strength: Double = 0.0) =
        PetState(PetStats(100.0, 100.0, 50.0, intellect, strength), stage, 0L, 0L)

    @Test
    fun monsterCard_followsTheEvolutionStage() {
        assertNull(MonsterCards.forPet(pet(Stage.EGG)))
        assertEquals(MonsterCard.PEEK, MonsterCards.forPet(pet(Stage.INFANT)))
        assertEquals(MonsterCard.REDRAW, MonsterCards.forPet(pet(Stage.GROWTH_1)))
        assertEquals(MonsterCard.REDRAW, MonsterCards.forPet(pet(Stage.GROWTH_2)))
    }

    @Test
    fun matureMonsterCard_dependsOnTheRoute() {
        assertEquals(MonsterCard.STEADY, MonsterCards.forPet(pet(Stage.MATURE, intellect = 50.0, strength = 10.0)))
        assertEquals(MonsterCard.ENDURE, MonsterCards.forPet(pet(Stage.MATURE, intellect = 10.0, strength = 50.0)))
        assertEquals(MonsterCard.STEADY, MonsterCards.forPet(pet(Stage.MATURE, intellect = 20.0, strength = 20.0))) // 同数は知力扱い
    }

    // --- 共通基盤との接続 ---

    @Test
    fun winAndDrawAreCounted_forTheSharedDailyCap() {
        var p = MiniGameProgress()
        val g = CardRewards.gains(result(MatchOutcome.WIN, NpcLevel.ADVANCED))
        repeat(4) { p = p.settle(MiniGame.CARD, 1L, RewardTier.ADVANCED, true, false, g).progress }
        // 16 × 4 = 64 だが 1 日の上限は 60
        assertEquals(60.0, p.daily.intellectGained, 1e-9)
    }

    @Test
    fun firstClearOfAdvanced_givesTheIntellectEquipment() {
        val s = MiniGameProgress().settle(MiniGame.CARD, 1L, RewardTier.ADVANCED, true, true, MinigameGains.NONE)
        assertEquals(1, s.progress.inventory.count(ItemType.EQUIP_INTELLECT))
    }
}
