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
        val s = PetSimulator.advance(state(), 2 * hour, config)
        assertEquals(88.0, s.stats.satiety, 1e-9) // 10分ごとに -1 = 1時間で -6
        assertEquals(88.0, s.stats.cleanliness, 1e-9) // 清潔度も 10分ごとに -1
        assertEquals(50.0, s.stats.mood, 1e-9) // 基準以上の間は機嫌は減らない
        assertEquals(2 * hour, s.lastUpdatedMs)
    }

    @Test
    fun decay_isCappedAt24Hours() {
        // 上限そのものを確かめるため、ゆっくり減る設定で 100時間放置(反映されるのは 24時間ぶんだけ)
        val slow = PetConfig(satietyDecayPerHour = 1.0, cleanlinessDecayPerHour = 0.7)
        val s = PetSimulator.advance(state(), 100 * hour, slow)
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

    // --- 機嫌の減少(4.1: 満腹度・清潔度のどちらかが60を下回っている間 -5/時間) ---

    @Test
    fun mood_doesNotDropWhileBothGaugesAtOrAboveThreshold() {
        val s = PetSimulator.advance(state(satiety = 100.0, cleanliness = 100.0), 6 * hour, config) // 満腹度は 64 まで
        assertEquals(50.0, s.stats.mood, 1e-9)
    }

    @Test
    fun mood_dropsForWholeWindowWhenSatietyAlreadyBelow() {
        val s = PetSimulator.advance(state(satiety = 50.0, mood = 50.0), 2 * hour, config)
        assertEquals(40.0, s.stats.mood, 1e-9) // 2時間 × -5
    }

    @Test
    fun mood_dropsOnlyAfterSatietyCrossesThreshold() {
        // 66 → 3時間後に 48。60 を下回るのは 1時間後以降なので、下回っている時間は 2時間
        val s = PetSimulator.advance(state(satiety = 66.0, mood = 50.0), 3 * hour, config)
        assertEquals(40.0, s.stats.mood, 1e-9)
    }

    @Test
    fun mood_dropsWhenCleanlinessBelowThreshold() {
        val s = PetSimulator.advance(state(satiety = 100.0, cleanliness = 50.0, mood = 50.0), 2 * hour, config)
        assertEquals(40.0, s.stats.mood, 1e-9)
    }

    @Test
    fun mood_dropsFromTheEarlierOfTheTwoCrossings() {
        // 清潔度 61 は 1/6 時間後に 60 を下回る。満腹度 100 は約 6.7 時間後。5時間の窓では清潔度側が先
        val s = PetSimulator.advance(state(satiety = 100.0, cleanliness = 61.0, mood = 50.0), 5 * hour, config)
        assertEquals(50.0 - 5.0 * (5.0 - 1.0 / 6.0), s.stats.mood, 1e-9)
    }

    @Test
    fun mood_clampsAtZero() {
        val s = PetSimulator.advance(state(satiety = 10.0, mood = 5.0), 3 * hour, config)
        assertEquals(0.0, s.stats.mood, 1e-9)
    }

    @Test
    fun mood_doesNotDropForEgg() {
        val egg = PetState(PetStats(10.0, 10.0, 50.0), Stage.EGG, 0L, 0L)
        assertEquals(50.0, PetSimulator.advance(egg, 30_000L, config).stats.mood, 1e-9)
    }

    // --- 上限(24時間)の方針と放置による死亡(4.4) ---

    @Test
    fun cappedDecayNeverChangesTheOutcome_gaugesBottomOutBeforeTheCap() {
        // 方針: 満タンから 24時間の上限より前に 0 へ届く速さにしておく
        assertTrue(100.0 / config.satietyDecayPerHour < config.decayCapHours)
        assertTrue(100.0 / config.cleanlinessDecayPerHour < config.decayCapHours)
        val atCap = PetSimulator.advance(state(), 24 * hour, config).stats
        val beyondCap = PetSimulator.advance(state(), 40 * hour, config).stats // 死亡(約 40時間40分)の手前
        assertEquals(atCap, beyondCap)
    }

    @Test
    fun satietyZeroSince_isBackCalculatedFromDecayRate() {
        val s = PetSimulator.advance(state(satiety = 12.0), 10 * hour, config) // 2時間後に 0 になる
        assertEquals(0.0, s.stats.satiety, 1e-9)
        assertEquals(2 * hour, s.satietyZeroSinceMs)
    }

    @Test
    fun satietyZeroSince_isNullWhileSatietyRemains() {
        assertNull(PetSimulator.advance(state(), 10 * hour, config).satietyZeroSinceMs)
    }

    @Test
    fun neglectDeath_afterSatietyZeroFor24HoursAndCleanlinessLow() {
        // 満腹度は 16時間40分後に 0。そこから 24時間 = 40時間40分後に死亡
        val before = PetSimulator.advance(state(), 40 * hour, config)
        assertEquals(Stage.INFANT, before.stage)
        val dead = PetSimulator.advance(state(), 41 * hour, config)
        assertEquals(Stage.EGG, dead.stage)
        assertEquals(2, dead.generation)
        assertEquals(41 * hour, dead.stageEnteredAtMs)
    }

    @Test
    fun neglectDeath_worksAcrossSeveralOpens() {
        val first = PetSimulator.advance(state(), 30 * hour, config) // 0 の継続はまだ 13時間20分
        assertEquals(Stage.INFANT, first.stage)
        assertEquals(60_000_000L, first.satietyZeroSinceMs)
        val second = PetSimulator.advance(first, 45 * hour, config)
        assertEquals(Stage.EGG, second.stage)
        assertEquals(2, second.generation)
    }

    @Test
    fun neglectDeath_notWhenCleanlinessAboveThreshold() {
        val s = PetState(PetStats(0.0, 60.0, 50.0), Stage.INFANT, 0L, 25 * hour, satietyZeroSinceMs = 0L)
        assertEquals(Stage.INFANT, PetSimulator.advance(s, 25 * hour, config).stage)
    }

    @Test
    fun neglectDeath_boundaryCleanlinessExactlyAtThresholdCounts() {
        val s = PetState(PetStats(0.0, 20.0, 50.0), Stage.INFANT, 0L, 25 * hour, satietyZeroSinceMs = 0L)
        assertEquals(Stage.EGG, PetSimulator.advance(s, 25 * hour, config).stage)
    }

    @Test
    fun feeding_breaksTheZeroStreak() {
        val zero = PetState(PetStats(0.0, 0.0, 0.0), Stage.INFANT, 0L, 10 * hour, satietyZeroSinceMs = 0L)
        val fed = PetSimulator.feed(zero, 10 * hour, 20.0, config)
        assertNull(fed.satietyZeroSinceMs)
        assertEquals(Stage.INFANT, fed.stage)
    }

    @Test
    fun careIsTooLateWhenDeathConditionAlreadyMet() {
        val zero = PetState(PetStats(0.0, 0.0, 0.0), Stage.INFANT, 0L, 25 * hour, satietyZeroSinceMs = 0L)
        val s = PetSimulator.feed(zero, 25 * hour, 100.0, config)
        assertEquals(Stage.EGG, s.stage)
        assertEquals(2, s.generation)
    }

    @Test
    fun eggNeverDies() {
        val egg = PetState(PetStats(0.0, 0.0, 0.0), Stage.EGG, 0L, 0L, satietyZeroSinceMs = 0L)
        assertEquals(Stage.EGG, PetSimulator.advance(egg, 30_000L, config).stage)
    }

    // --- お世話 ---

    @Test
    fun feed_clampsAt100() {
        val s = PetSimulator.feed(state(satiety = 90.0), 0, 25.0, config)
        assertEquals(100.0, s.stats.satiety, 1e-9)
    }

    @Test
    fun feed_appliesDecayFirst() {
        val s = PetSimulator.feed(state(satiety = 50.0), 1 * hour, 20.0, config)
        assertEquals(64.0, s.stats.satiety, 1e-9) // 50 - 6 + 20
    }

    @Test
    fun clean_addsAmount() {
        val s = PetSimulator.clean(state(cleanliness = 40.0), 0, 20.0, config)
        assertEquals(60.0, s.stats.cleanliness, 1e-9)
    }

    @Test
    fun feed_raisesMood() {
        val s = PetSimulator.feed(state(mood = 40.0), 0, 20.0, config)
        assertEquals(45.0, s.stats.mood, 1e-9)
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
        assertEquals("sad", PetAppearance.baseAnimation(PetStats(100.0, 100.0, 30.0), config)) // 機嫌30以下
        assertEquals("idle", PetAppearance.baseAnimation(PetStats(100.0, 100.0, 30.1), config))
        assertEquals("sad", PetAppearance.baseAnimation(PetStats(30.0, 100.0, 80.0), config)) // 満腹度30以下
        assertEquals("idle", PetAppearance.baseAnimation(PetStats(30.1, 100.0, 80.0), config))
        assertEquals("dirty", PetAppearance.baseAnimation(PetStats(100.0, 0.0, 80.0), config))
        assertEquals("dirty", PetAppearance.baseAnimation(PetStats(100.0, 0.0, 10.0), config)) // 暫定: 清潔度0を優先
    }

    @Test
    fun appearance_satietyCanBeIgnored() {
        val c = PetConfig(sadSatietyThreshold = null)
        assertEquals("idle", PetAppearance.baseAnimation(PetStats(5.0, 100.0, 80.0), c))
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
    fun newEgg_startsAsEgg() {
        val s = PetState.newEgg(1000L, config)
        assertEquals(Stage.EGG, s.stage)
        assertEquals(1000L, s.stageEnteredAtMs)
    }

    // --- 卵(ステータスなし。幼年期から初期値を付与) ---

    @Test
    fun egg_doesNotDecayOrTakeCare() {
        val egg = PetState.newEgg(0L, config)
        val later = PetSimulator.advance(egg, 30_000L, config) // 孵化前(30秒)
        assertEquals(egg.stats, later.stats)
        assertEquals(Stage.EGG, later.stage)
        val fed = PetSimulator.feed(egg, 30_000L, 50.0, config)
        assertEquals(egg.stats, fed.stats)
        assertEquals(Stage.EGG, fed.stage)
    }

    @Test
    fun egg_hatchesByTimeAloneAndGetsInitialStats() {
        val egg = PetState.newEgg(0L, config).copy(stats = PetStats(0.0, 0.0, 0.0)) // 値が低くても孵化する
        val s = PetSimulator.advance(egg, 60_000L, config)
        assertEquals(Stage.INFANT, s.stage)
        assertEquals(60_000L, s.stageEnteredAtMs)
        assertEquals(PetStats(100.0, 100.0, 50.0), s.stats)
        assertEquals(60_000L, s.lastUpdatedMs)
    }

    @Test
    fun infant_decaysFromHatchTime() {
        val hatched = PetSimulator.advance(PetState.newEgg(0L, config), 60_000L, config)
        val s = PetSimulator.advance(hatched, 60_000L + 5 * hour, config)
        assertEquals(70.0, s.stats.satiety, 1e-9)
    }
}
