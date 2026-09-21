package com.nebu965039.monsterraising.core.minigame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MiniGameProgressTest {
    private val config = MiniGameConfig()
    private val day = 100L

    private fun MiniGameProgress.play(game: MiniGame = MiniGame.PUZZLE, on: Long = day) = startPlay(game, on, config)!!

    // --- 1 日のプレイ回数(2.2) ---

    @Test
    fun threePlaysPerDayPerGame() {
        var p = MiniGameProgress()
        assertEquals(3, p.playStatus(MiniGame.PUZZLE, day, config).remaining)
        repeat(3) { p = p.play() }
        assertEquals(0, p.playStatus(MiniGame.PUZZLE, day, config).remaining)
        assertNull(p.startPlay(MiniGame.PUZZLE, day, config))
    }

    @Test
    fun gamesAreCountedIndependently() {
        var p = MiniGameProgress()
        repeat(3) { p = p.play(MiniGame.PUZZLE) }
        assertEquals(3, p.playStatus(MiniGame.CARD, day, config).remaining)
        assertNotNull(p.startPlay(MiniGame.CARD, day, config))
    }

    @Test
    fun dailyCountsResetOnANewDay() {
        var p = MiniGameProgress()
        repeat(3) { p = p.play() }
        assertNull(p.startPlay(MiniGame.PUZZLE, day, config))
        assertEquals(3, p.playStatus(MiniGame.PUZZLE, day + 1, config).remaining)
        assertNotNull(p.startPlay(MiniGame.PUZZLE, day + 1, config))
    }

    // --- 広告による追加(2.2) ---

    @Test
    fun adAddsOnePlay_upToTwoPerDay() {
        var p = MiniGameProgress()
        repeat(3) { p = p.play() }
        p = p.addAdPlay(MiniGame.PUZZLE, day, config)!!
        assertEquals(1, p.playStatus(MiniGame.PUZZLE, day, config).remaining)
        p = p.addAdPlay(MiniGame.PUZZLE, day, config)!!
        assertEquals(2, p.playStatus(MiniGame.PUZZLE, day, config).remaining)
        assertFalse(p.playStatus(MiniGame.PUZZLE, day, config).canWatchAd)
        assertNull(p.addAdPlay(MiniGame.PUZZLE, day, config)) // 3 回目は不可
    }

    @Test
    fun maximumIsFivePlaysPerGamePerDay() {
        var p = MiniGameProgress()
        repeat(3) { p = p.play() }
        p = p.addAdPlay(MiniGame.PUZZLE, day, config)!!.addAdPlay(MiniGame.PUZZLE, day, config)!!
        repeat(2) { p = p.play() }
        assertEquals(5, p.playStatus(MiniGame.PUZZLE, day, config).playsToday)
        assertNull(p.startPlay(MiniGame.PUZZLE, day, config))
    }

    @Test
    fun adBonusIsPerGame() {
        val p = MiniGameProgress().addAdPlay(MiniGame.PUZZLE, day, config)!!
        assertEquals(4, p.playStatus(MiniGame.PUZZLE, day, config).allowedPlays)
        assertEquals(3, p.playStatus(MiniGame.CARD, day, config).allowedPlays)
    }

    @Test
    fun adBonusResetsOnANewDay() {
        val p = MiniGameProgress().addAdPlay(MiniGame.PUZZLE, day, config)!!.addAdPlay(MiniGame.PUZZLE, day, config)!!
        assertTrue(p.playStatus(MiniGame.PUZZLE, day + 1, config).canWatchAd)
        assertEquals(3, p.playStatus(MiniGame.PUZZLE, day + 1, config).allowedPlays)
    }

    // --- ミニゲーム券(8.4) ---

    @Test
    fun ticketAddsOnePlay_andIsConsumed() {
        val p = MiniGameProgress(inventory = Inventory().add(ItemType.MINIGAME_TICKET, 2))
        val used = p.addTicketPlay(MiniGame.CARD, day)!!
        assertEquals(1, used.inventory.count(ItemType.MINIGAME_TICKET))
        assertEquals(4, used.playStatus(MiniGame.CARD, day, config).allowedPlays)
    }

    @Test
    fun noTicket_noExtraPlay() {
        assertNull(MiniGameProgress().addTicketPlay(MiniGame.PUZZLE, day))
    }

    @Test
    fun ticketsAndAdsStack() {
        var p = MiniGameProgress(inventory = Inventory().add(ItemType.MINIGAME_TICKET, 1))
        p = p.addAdPlay(MiniGame.PUZZLE, day, config)!!.addTicketPlay(MiniGame.PUZZLE, day)!!
        assertEquals(5, p.playStatus(MiniGame.PUZZLE, day, config).allowedPlays)
    }

    @Test
    fun ticketBonusDoesNotCarryToTheNextDay() {
        val p = MiniGameProgress(inventory = Inventory().add(ItemType.MINIGAME_TICKET, 1)).addTicketPlay(MiniGame.PUZZLE, day)!!
        assertEquals(3, p.playStatus(MiniGame.PUZZLE, day + 1, config).allowedPlays)
        assertEquals(0, p.playStatus(MiniGame.PUZZLE, day + 1, config).tickets)
    }

    // --- 有効度の 1 日の上限(5.4) ---

    private fun settle(p: MiniGameProgress, gains: MinigameGains, cap: Double? = 60.0, on: Long = day) =
        p.settle(MiniGame.PUZZLE, on, RewardTier.BEGINNER, cleared = false, firstClear = false, gains = gains, config = config.copy(dailyEffectivenessCap = cap))

    @Test
    fun gainsBelowTheCapAreApplied() {
        val s = settle(MiniGameProgress(), MinigameGains(10.0, 8.0, 10.0))
        assertEquals(MinigameGains(10.0, 8.0, 10.0), s.appliedGains)
        assertFalse(s.capped)
    }

    @Test
    fun capIsAppliedPerAxisAcrossPlays() {
        var p = MiniGameProgress()
        p = settle(p, MinigameGains(40.0, 10.0, 10.0)).progress
        val s = settle(p, MinigameGains(40.0, 10.0, 10.0))
        assertEquals(20.0, s.appliedGains.intellect, 1e-9) // 60 - 40
        assertEquals(10.0, s.appliedGains.strength, 1e-9) // 筋力は上限内
        assertTrue(s.capped)
        assertEquals(60.0, s.progress.daily.intellectGained, 1e-9)
    }

    @Test
    fun onceTheCapIsReached_nothingMoreIsApplied_butMoodStill() {
        var p = MiniGameProgress()
        p = settle(p, MinigameGains(60.0, 60.0, 10.0)).progress
        val s = settle(p, MinigameGains(5.0, 5.0, 10.0))
        assertEquals(MinigameGains(0.0, 0.0, 10.0), s.appliedGains)
        assertTrue(s.capped)
    }

    @Test
    fun capResetsOnANewDay() {
        val p = settle(MiniGameProgress(), MinigameGains(60.0, 60.0, 10.0)).progress
        val s = settle(p, MinigameGains(30.0, 30.0, 10.0), on = day + 1)
        assertEquals(30.0, s.appliedGains.intellect, 1e-9)
        assertFalse(s.capped)
    }

    @Test
    fun noCap_whenConfiguredAsNull() {
        val s = settle(MiniGameProgress(), MinigameGains(1_000.0, 1_000.0, 10.0), cap = null)
        assertEquals(1_000.0, s.appliedGains.intellect, 1e-9)
        assertFalse(s.capped)
    }

    // --- クリア報酬(8.5) ---

    @Test
    fun rewardTable_matchesTheDesign() {
        val b = ClearRewardTable.rewards(MiniGame.PUZZLE, RewardTier.BEGINNER, firstClear = true)
        assertEquals(50, b.explorationPoints)
        assertEquals(mapOf(ItemType.RICE to 1), b.items) // 初級は初回でも装備なし

        val i = ClearRewardTable.rewards(MiniGame.PUZZLE, RewardTier.INTERMEDIATE, firstClear = true)
        assertEquals(75, i.explorationPoints)
        assertEquals(mapOf(ItemType.RICE to 1, ItemType.MINIGAME_TICKET to 1), i.items)

        val a = ClearRewardTable.rewards(MiniGame.PUZZLE, RewardTier.ADVANCED, firstClear = false)
        assertEquals(100, a.explorationPoints)
        assertEquals(mapOf(ItemType.RICE to 1, ItemType.MINIGAME_TICKET to 2), a.items)
    }

    @Test
    fun advancedFirstClear_addsThatGamesEquipment() {
        assertEquals(1, ClearRewardTable.rewards(MiniGame.PUZZLE, RewardTier.ADVANCED, true).items[ItemType.EQUIP_BALANCE_EFFECT])
        assertEquals(1, ClearRewardTable.rewards(MiniGame.CARD, RewardTier.ADVANCED, true).items[ItemType.EQUIP_INTELLECT])
        assertEquals(1, ClearRewardTable.rewards(MiniGame.WALL_BREAK, RewardTier.ADVANCED, true).items[ItemType.EQUIP_STRENGTH])
        assertNull(ClearRewardTable.rewards(MiniGame.PUZZLE, RewardTier.ADVANCED, false).items[ItemType.EQUIP_BALANCE_EFFECT])
    }

    @Test
    fun settle_addsRewardsOnlyWhenCleared() {
        val notCleared = MiniGameProgress().settle(MiniGame.PUZZLE, day, RewardTier.INTERMEDIATE, false, false, MinigameGains.NONE, config)
        assertNull(notCleared.rewards)
        assertEquals(0, notCleared.progress.inventory.explorationPoints)

        val cleared = MiniGameProgress().settle(MiniGame.PUZZLE, day, RewardTier.INTERMEDIATE, true, true, MinigameGains.NONE, config)
        assertEquals(75, cleared.progress.inventory.explorationPoints)
        assertEquals(1, cleared.progress.inventory.count(ItemType.RICE))
        assertEquals(1, cleared.progress.inventory.count(ItemType.MINIGAME_TICKET))
    }

    @Test
    fun rewardsAccumulateAcrossPlays() {
        var p = MiniGameProgress()
        repeat(2) { p = p.settle(MiniGame.PUZZLE, day, RewardTier.BEGINNER, true, false, MinigameGains.NONE, config).progress }
        assertEquals(100, p.inventory.explorationPoints)
        assertEquals(2, p.inventory.count(ItemType.RICE))
    }

    @Test
    fun earnedTicketsCanBeUsedForExtraPlays() {
        var p = MiniGameProgress().settle(MiniGame.PUZZLE, day, RewardTier.ADVANCED, true, false, MinigameGains.NONE, config).progress
        assertEquals(2, p.inventory.count(ItemType.MINIGAME_TICKET))
        p = p.addTicketPlay(MiniGame.PUZZLE, day)!!
        assertEquals(4, p.playStatus(MiniGame.PUZZLE, day, config).allowedPlays)
    }

    // --- 持ち物 ---

    @Test
    fun inventory_consumeFailsWhenShort() {
        val inv = Inventory().add(ItemType.RICE, 1)
        assertNull(inv.consume(ItemType.RICE, 2))
        assertEquals(0, inv.consume(ItemType.RICE, 1)!!.count(ItemType.RICE))
        assertEquals(1, inv.count(ItemType.RICE)) // 元は変わらない
    }

    // --- 日付 ---

    @Test
    fun dayIndex_changesAtLocalMidnight() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val t0 = java.time.ZonedDateTime.of(2026, 9, 21, 23, 59, 59, 0, tokyo).toInstant().toEpochMilli()
        val t1 = java.time.ZonedDateTime.of(2026, 9, 22, 0, 0, 0, 0, tokyo).toInstant().toEpochMilli()
        assertEquals(DayClock.dayIndex(t0, tokyo) + 1, DayClock.dayIndex(t1, tokyo))
        assertEquals(DayClock.dayIndex(t0, tokyo), DayClock.dayIndex(t0 - 3_600_000L, tokyo))
    }

    @Test
    fun dayIndex_respectsTheDayStartHour() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val threeAm = java.time.ZonedDateTime.of(2026, 9, 22, 3, 0, 0, 0, tokyo).toInstant().toEpochMilli()
        val fiveAm = java.time.ZonedDateTime.of(2026, 9, 22, 5, 0, 0, 0, tokyo).toInstant().toEpochMilli()
        assertEquals(DayClock.dayIndex(threeAm, tokyo, 4) + 1, DayClock.dayIndex(fiveAm, tokyo, 4))
    }

    // --- 保存形式 ---

    @Test
    fun codec_roundTripsAndRejectsGarbage() {
        val p = MiniGameProgress()
            .settle(MiniGame.PUZZLE, day, RewardTier.ADVANCED, true, true, MinigameGains(5.0, 5.0, 10.0), config).progress
            .startPlay(MiniGame.CARD, day, config)!!
        assertEquals(p, MiniGameProgressCodec.decode(MiniGameProgressCodec.encode(p)))
        assertNull(MiniGameProgressCodec.decode("{ broken"))
    }
}
