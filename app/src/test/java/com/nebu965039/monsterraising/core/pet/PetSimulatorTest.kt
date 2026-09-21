package com.nebu965039.monsterraising.core.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetSimulatorTest {
    private val config = PetConfig()
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun state(
        satiety: Double = 100.0,
        cleanliness: Double = 100.0,
        mood: Double = 50.0,
        stage: Stage = Stage.INFANT,
        enteredAt: Long = 0L,
        last: Long = 0L,
    ) = PetState(PetStats(satiety, cleanliness, mood), stage, enteredAt, last)

    // --- 自然減少(4.1) ---

    @Test
    fun decay_perHour() {
        val s = PetSimulator.advance(state(), 10 * hour, config)
        assertEquals(90.0, s.stats.satiety, 1e-9)
        assertEquals(93.0, s.stats.cleanliness, 1e-9)
        assertEquals(50.0, s.stats.mood, 1e-9) // 機嫌は自然減少しない(連動式は未決)
        assertEquals(10 * hour, s.lastUpdatedMs)
    }

    @Test
    fun decay_isCappedAt24Hours() {
        val s = PetSimulator.advance(state(), 100 * hour, config)
        assertEquals(76.0, s.stats.satiety, 1e-9)
        assertEquals(100.0 - 0.7 * 24, s.stats.cleanliness, 1e-9)
    }

    @Test
    fun decay_clampsAtZero() {
        val s = PetSimulator.advance(state(satiety = 5.0, cleanliness = 3.0), 24 * hour, config)
        assertEquals(0.0, s.stats.satiety, 1e-9)
        assertEquals(0.0, s.stats.cleanliness, 1e-9)
    }

    @Test
    fun clockGoingBackwards_doesNotIncreaseStats() {
        val s = PetSimulator.advance(state(satiety = 50.0, last = 10 * hour), 5 * hour, config)
        assertEquals(50.0, s.stats.satiety, 1e-9)
    }

    // --- お世話 ---

    @Test
    fun feed_clampsAt100() {
        val s = PetSimulator.feed(state(satiety = 90.0), 0, 25.0, config)
        assertEquals(100.0, s.stats.satiety, 1e-9)
    }

    @Test
    fun feed_appliesDecayFirst() {
        val s = PetSimulator.feed(state(satiety = 50.0), 10 * hour, 20.0, config)
        assertEquals(60.0, s.stats.satiety, 1e-9) // 50 - 10 + 20
    }

    @Test
    fun clean_addsAmount() {
        val s = PetSimulator.clean(state(cleanliness = 40.0), 0, 20.0, config)
        assertEquals(60.0, s.stats.cleanliness, 1e-9)
    }

    @Test
    fun pet_addsMood() {
        val s = PetSimulator.pet(state(mood = 25.0), 0, config)
        assertEquals(35.0, s.stats.mood, 1e-9)
    }

    @Test
    fun effectiveness_neverDecreases() {
        val base = PetStats(50.0, 50.0, 50.0, intellect = 10.0, strength = 5.0)
        assertEquals(10.0, base.addIntellect(-3.0).intellect, 1e-9)
        assertEquals(8.0, base.addStrength(3.0).strength, 1e-9)
        val s = PetSimulator.advance(PetState(base, Stage.INFANT, 0, 0), 24 * hour, config)
        assertEquals(10.0, s.stats.intellect, 1e-9)
        assertEquals(5.0, s.stats.strength, 1e-9)
    }

    // --- 進化(4.3) ---

    @Test
    fun evolution_waitsUntilStageDurationElapsed() {
        val c = Evolution.check(Stage.INFANT, 0, day - 1, PetStats(100.0, 100.0, 50.0), config)
        assertEquals(EvolutionCheck.Waiting(1), c)
    }

    @Test
    fun evolution_readyWhenTimeAndGaugesSatisfied_exactBoundaries() {
        val c = Evolution.check(Stage.INFANT, 0, day, PetStats(60.0, 60.0, 0.0), config)
        assertEquals(EvolutionCheck.Ready(Stage.GROWTH_1), c)
    }

    @Test
    fun evolution_pendingWhenGaugeBelowThreshold() {
        val low1 = Evolution.check(Stage.INFANT, 0, day, PetStats(59.9, 100.0, 50.0), config)
        val low2 = Evolution.check(Stage.INFANT, 0, day, PetStats(100.0, 59.9, 50.0), config)
        assertEquals(EvolutionCheck.Pending, low1)
        assertEquals(EvolutionCheck.Pending, low2)
    }

    @Test
    fun evolution_matureIsFinal() {
        assertEquals(EvolutionCheck.Final, Evolution.check(Stage.MATURE, 0, 100 * day, PetStats(100.0, 100.0, 50.0), config))
    }

    @Test
    fun advance_evolvesAndRestartsStageClock() {
        val s = PetSimulator.advance(state(stage = Stage.EGG), 2 * 60_000L, config)
        assertEquals(Stage.INFANT, s.stage)
        assertEquals(2 * 60_000L, s.stageEnteredAtMs)
    }

    @Test
    fun advance_movesOnlyOneStagePerCall() {
        val s = PetSimulator.advance(state(stage = Stage.EGG), 100 * day, config)
        assertEquals(Stage.INFANT, s.stage)
    }

    @Test
    fun pendingEvolution_resolvesAfterCare() {
        // 幼年期の期間を満たしたが満腹度が低く保留 → 餌やりで基準を満たした時点で進化する
        val low = state(satiety = 30.0, stage = Stage.INFANT, enteredAt = 0, last = day)
        val stay = PetSimulator.advance(low, day, config)
        assertEquals(Stage.INFANT, stay.stage) // 保留(ステータスの巻き戻しなし)
        assertEquals(30.0, stay.stats.satiety, 1e-9)

        val fed = PetSimulator.feed(stay, day, 40.0, config)
        assertEquals(Stage.GROWTH_1, fed.stage)
        assertEquals(day, fed.stageEnteredAtMs)
    }

    // --- 通常ルート(4.3) ---

    @Test
    fun normalRoute_comparesEffectiveness() {
        assertEquals(Route.INTELLECT, Evolution.normalRoute(PetStats(1.0, 1.0, 1.0, intellect = 5.0, strength = 3.0)))
        assertEquals(Route.STRENGTH, Evolution.normalRoute(PetStats(1.0, 1.0, 1.0, intellect = 3.0, strength = 5.0)))
        assertNull(Evolution.normalRoute(PetStats(1.0, 1.0, 1.0, intellect = 4.0, strength = 4.0)))
    }

    // --- 見た目の連動(4.6) ---

    @Test
    fun appearance_followsStats() {
        assertEquals("idle", PetAppearance.baseAnimation(PetStats(100.0, 100.0, 80.0), config))
        assertEquals("sad", PetAppearance.baseAnimation(PetStats(100.0, 100.0, 29.9), config))
        assertEquals("idle", PetAppearance.baseAnimation(PetStats(100.0, 100.0, 30.0), config))
        assertEquals("dirty", PetAppearance.baseAnimation(PetStats(100.0, 0.0, 80.0), config))
        assertEquals("dirty", PetAppearance.baseAnimation(PetStats(100.0, 0.0, 10.0), config)) // 暫定: 清潔度0を優先
    }

    @Test
    fun appearance_satietyThresholdIsOptional() {
        val c = PetConfig(sadSatietyThreshold = 20.0)
        assertEquals("sad", PetAppearance.baseAnimation(PetStats(19.0, 100.0, 80.0), c))
        assertEquals("idle", PetAppearance.baseAnimation(PetStats(19.0, 100.0, 80.0), config))
    }

    // --- 保存形式 ---

    @Test
    fun codec_roundTrips() {
        val s = PetState(PetStats(70.5, 33.3, 12.0, intellect = 7.0, strength = 2.0), Stage.GROWTH_2, 123L, 456L)
        assertEquals(s, PetStateCodec.decode(PetStateCodec.encode(s)))
    }

    @Test
    fun codec_returnsNullForCorruptData() {
        assertNull(PetStateCodec.decode("{ broken"))
        assertNull(PetStateCodec.decode("""{"stage":"UNKNOWN_STAGE"}"""))
        assertNull(PetStateCodec.decode(""))
    }

    @Test
    fun newEgg_startsFromConfig() {
        val s = PetState.newEgg(1000L, config)
        assertEquals(Stage.EGG, s.stage)
        assertEquals(1000L, s.stageEnteredAtMs)
        assertTrue(s.stats.satiety > 0)
    }
}
