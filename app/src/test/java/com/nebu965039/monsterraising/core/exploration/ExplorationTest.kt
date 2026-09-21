package com.nebu965039.monsterraising.core.exploration

import com.nebu965039.monsterraising.core.minigame.Inventory
import com.nebu965039.monsterraising.core.minigame.ItemType
import com.nebu965039.monsterraising.core.minigame.MiniGameProgress
import com.nebu965039.monsterraising.core.minigame.MiniGameProgressCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExplorationTest {
    private val config = ExplorationConfig()
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun withPoints(n: Int) = MiniGameProgress(inventory = Inventory(explorationPoints = n))

    private fun started(p: MiniGameProgress, site: ExplorationSite = ExplorationSite.CAVE, plan: ExplorationPlan = ExplorationPlan.SHORT, now: Long = 0L) =
        (Exploration.start(p, site, plan, now, Random(1)) as StartResult.Started).progress

    // --- 設計値(8.2) ---

    @Test
    fun costsAndDurations_matchTheDesign() {
        assertEquals(100, config.cost(ExplorationPlan.SHORT))
        assertEquals(30 * minute, config.duration(ExplorationPlan.SHORT))
        assertEquals(1_000, config.cost(ExplorationPlan.LONG))
        assertEquals(4 * hour, config.duration(ExplorationPlan.LONG)) // 5 時間ではなく、ボーナスで 4 時間
        assertEquals(1, ExplorationPlan.SHORT.runs)
        assertEquals(10, ExplorationPlan.LONG.runs)
    }

    @Test
    fun threeSites_withTheirEquipment() {
        assertEquals(ItemType.EQUIP_BALANCE_CARE, ExplorationSite.CAVE.equipment)
        assertEquals(ItemType.EQUIP_CLEANLINESS, ExplorationSite.COAST.equipment)
        assertEquals(ItemType.EQUIP_SATIETY, ExplorationSite.MOUNTAIN.equipment)
        assertEquals("暗くて巨大な洞窟があります。", ExplorationSite.CAVE.intro)
    }

    // --- 報酬の抽選(8.3) ---

    @Test
    fun aRun_givesThreeItems_andTenRunsGiveThirty() {
        assertEquals(3, Exploration.rollRewards(ExplorationSite.CAVE, ExplorationPlan.SHORT, Random(1)).values.sum())
        assertEquals(30, Exploration.rollRewards(ExplorationSite.CAVE, ExplorationPlan.LONG, Random(1)).values.sum())
    }

    @Test
    fun rarities_followTheDesignProbabilities() {
        val random = Random(42)
        val n = 200_000
        val counts = mutableMapOf<ItemType, Int>()
        repeat(n) { val i = Exploration.rollItem(ExplorationSite.CAVE, random); counts[i] = (counts[i] ?: 0) + 1 }
        fun rate(i: ItemType) = (counts[i] ?: 0).toDouble() / n
        assertEquals(0.915, rate(ItemType.RICE), 0.005)
        assertEquals(0.05, rate(ItemType.MINIGAME_TICKET), 0.003)
        assertEquals(0.03, rate(ItemType.EQUIP_BALANCE_CARE), 0.003)
        assertEquals(0.005, rate(ItemType.ELIXIR), 0.0015)
    }

    @Test
    fun theEquipmentDependsOnTheSite() {
        for (site in ExplorationSite.entries) {
            val items = List(50_000) { Exploration.rollItem(site, Random(it)) }.toSet()
            assertTrue(site.equipment in items)
            // 他の拠点の装備は出ない
            for (other in ExplorationSite.entries - site) assertFalse(other.equipment in items)
        }
    }

    @Test
    fun onlyTheFiveExpectedKindsCanDrop() {
        val items = List(20_000) { Exploration.rollItem(ExplorationSite.COAST, Random(it)) }.toSet()
        assertTrue(items.all { it in setOf(ItemType.RICE, ItemType.MINIGAME_TICKET, ItemType.EQUIP_CLEANLINESS, ItemType.ELIXIR) })
    }

    @Test
    fun rollingIsReproducibleWithTheSameSeed() {
        assertEquals(
            Exploration.rollRewards(ExplorationSite.MOUNTAIN, ExplorationPlan.LONG, Random(9)),
            Exploration.rollRewards(ExplorationSite.MOUNTAIN, ExplorationPlan.LONG, Random(9)),
        )
    }

    // --- 開始 ---

    @Test
    fun start_spendsPoints_andSetsTheEndTime() {
        val p = started(withPoints(250), now = 1_000L)
        assertEquals(150, p.inventory.explorationPoints)
        val a = p.exploration.actives.first()
        assertEquals(ExplorationSite.CAVE, a.site)
        assertEquals(1_000L + 30 * minute, a.endsAtMs)
        assertEquals(3, a.rewards.values.sum())
    }

    @Test
    fun longStart_costsTenRunsAndTakesFourHours() {
        val p = started(withPoints(1_000), plan = ExplorationPlan.LONG, now = 0L)
        assertEquals(0, p.inventory.explorationPoints)
        assertEquals(4 * hour, p.exploration.actives.first().endsAtMs)
        assertEquals(30, p.exploration.actives.first().rewards.values.sum())
    }

    @Test
    fun start_failsWithoutEnoughPoints_andChangesNothing() {
        val p = withPoints(99)
        val r = Exploration.start(p, ExplorationSite.CAVE, ExplorationPlan.SHORT, 0L)
        assertEquals(StartResult.NotEnoughPoints(100, 99), r)
        assertEquals(StartResult.NotEnoughPoints(1_000, 999), Exploration.start(withPoints(999), ExplorationSite.CAVE, ExplorationPlan.LONG, 0L))
    }

    @Test
    fun withoutFriends_onlyOneSiteCanBeExploredAtATime() {
        val p = started(withPoints(500))
        assertEquals(StartResult.NoFreeSlot(1), Exploration.start(p, ExplorationSite.COAST, ExplorationPlan.SHORT, 0L))
        assertEquals(400, p.inventory.explorationPoints) // 失敗してもポイントは減らない
    }

    @Test
    fun theSameSiteCannotBeExploredTwice() {
        val p = started(withPoints(500))
        assertEquals(StartResult.SiteBusy, Exploration.start(p, ExplorationSite.CAVE, ExplorationPlan.SHORT, 0L, hasFriends = true))
    }

    // --- フレンドの協力(8.6): 合計 3 か所まで ---

    @Test
    fun capacity_isOneWithoutFriends_threeWithFriends() {
        assertEquals(1, config.capacity(false))
        assertEquals(3, config.capacity(true))
    }

    @Test
    fun withFriends_threeSitesCanBeExploredAtOnce() {
        var p = withPoints(1_000)
        for (site in ExplorationSite.entries) {
            val r = Exploration.start(p, site, ExplorationPlan.SHORT, 0L, Random(1), config, hasFriends = true)
            p = (r as StartResult.Started).progress
        }
        assertEquals(3, p.exploration.actives.size)
        assertEquals(700, p.inventory.explorationPoints)
    }

    @Test
    fun aFourthExplorationIsImpossible_evenWithFriends() {
        // 拠点は 3 か所しかないので、3 か所が埋まれば、あとは拠点が重なるか枠が埋まっている
        var p = withPoints(1_000)
        for (site in ExplorationSite.entries) p = (Exploration.start(p, site, ExplorationPlan.SHORT, 0L, Random(1), config, true) as StartResult.Started).progress
        assertEquals(StartResult.SiteBusy, Exploration.start(p, ExplorationSite.CAVE, ExplorationPlan.SHORT, 0L, hasFriends = true))
    }

    @Test
    fun friendsGoAway_existingExplorationsContinue_butNoNewOnesStart() {
        var p = withPoints(1_000)
        p = (Exploration.start(p, ExplorationSite.CAVE, ExplorationPlan.SHORT, 0L, Random(1), config, true) as StartResult.Started).progress
        p = (Exploration.start(p, ExplorationSite.COAST, ExplorationPlan.SHORT, 0L, Random(1), config, true) as StartResult.Started).progress
        assertEquals(StartResult.NoFreeSlot(1), Exploration.start(p, ExplorationSite.MOUNTAIN, ExplorationPlan.SHORT, 0L, hasFriends = false))
        assertEquals(2, p.exploration.actives.size)
    }

    @Test
    fun friendHelpMessage_appearsFromTheSecondSiteOn() {
        val none = withPoints(500)
        assertFalse(Exploration.isFriendHelp(none))
        val one = (Exploration.start(none, ExplorationSite.CAVE, ExplorationPlan.SHORT, 0L, Random(1), config, true) as StartResult.Started).progress
        assertTrue(Exploration.isFriendHelp(one))
    }

    @Test
    fun eachSiteIsTrackedAndCollectedSeparately() {
        var p = withPoints(2_000)
        p = (Exploration.start(p, ExplorationSite.CAVE, ExplorationPlan.SHORT, 0L, Random(1), config, true) as StartResult.Started).progress
        p = (Exploration.start(p, ExplorationSite.COAST, ExplorationPlan.LONG, 0L, Random(2), config, true) as StartResult.Started).progress
        assertTrue(Exploration.status(p, ExplorationSite.CAVE, 30 * minute) is ExplorationStatus.Finished)
        assertTrue(Exploration.status(p, ExplorationSite.COAST, 30 * minute) is ExplorationStatus.InProgress)
        assertEquals(ExplorationStatus.Idle, Exploration.status(p, ExplorationSite.MOUNTAIN, 30 * minute))
        val c = Exploration.collect(p, ExplorationSite.CAVE, 30 * minute)!!
        assertEquals(1, c.progress.exploration.actives.size) // 海岸は探索中のまま
        assertNull(Exploration.collect(c.progress, ExplorationSite.COAST, 30 * minute))
    }

    // --- 全体の状況とウィジェットの表示(8.6) ---

    @Test
    fun summaryLine_isNullWhenNothingIsGoingOn() {
        assertNull(Exploration.summaryLine(Exploration.overview(withPoints(0), 0L)))
    }

    @Test
    fun summaryLine_showsTheRemainingTimeWhileExploring() {
        val p = started(withPoints(1_000), plan = ExplorationPlan.LONG)
        assertEquals("探索中(残り 3時間30分)", Exploration.summaryLine(Exploration.overview(p, 30 * minute)))
    }

    @Test
    fun summaryLine_showsCompletionOnceFinished() {
        val p = started(withPoints(100))
        assertEquals("探索完了!", Exploration.summaryLine(Exploration.overview(p, 30 * minute)))
    }

    @Test
    fun summaryLine_withSeveralSites() {
        var p = withPoints(2_000)
        p = (Exploration.start(p, ExplorationSite.CAVE, ExplorationPlan.SHORT, 0L, Random(1), config, true) as StartResult.Started).progress
        p = (Exploration.start(p, ExplorationSite.COAST, ExplorationPlan.LONG, 0L, Random(2), config, true) as StartResult.Started).progress
        assertEquals("探索中 2か所(最短 残り 20分)", Exploration.summaryLine(Exploration.overview(p, 10 * minute)))
        // 洞窟が終わった: 完了が優先して表示される
        assertEquals("探索完了!", Exploration.summaryLine(Exploration.overview(p, 30 * minute)))
    }

    @Test
    fun overview_sortsBySoonestEnd() {
        var p = withPoints(2_000)
        p = (Exploration.start(p, ExplorationSite.COAST, ExplorationPlan.LONG, 0L, Random(2), config, true) as StartResult.Started).progress
        p = (Exploration.start(p, ExplorationSite.CAVE, ExplorationPlan.SHORT, 0L, Random(1), config, true) as StartResult.Started).progress
        assertEquals(listOf(ExplorationSite.CAVE, ExplorationSite.COAST), Exploration.overview(p, 0L).inProgress.map { it.site })
    }

    // --- 状況と受け取り ---

    @Test
    fun status_goesFromIdleToInProgressToFinished() {
        assertEquals(ExplorationStatus.Idle, Exploration.status(withPoints(100), ExplorationSite.CAVE, 0L))
        val p = started(withPoints(100))
        val mid = Exploration.status(p, ExplorationSite.CAVE, 10 * minute) as ExplorationStatus.InProgress
        assertEquals(20 * minute, mid.remainingMs)
        assertEquals(ExplorationSite.CAVE, mid.site)
        assertTrue(Exploration.status(p, ExplorationSite.CAVE, 30 * minute - 1) is ExplorationStatus.InProgress)
        assertTrue(Exploration.status(p, ExplorationSite.CAVE, 30 * minute) is ExplorationStatus.Finished)
    }

    @Test
    fun collect_isImpossibleBeforeTheEnd() {
        val p = started(withPoints(100))
        assertNull(Exploration.collect(p, ExplorationSite.CAVE, 30 * minute - 1))
        assertNull(Exploration.collect(withPoints(100), ExplorationSite.CAVE, 0L))
    }

    @Test
    fun collect_addsTheRolledRewards_andClearsTheExploration() {
        val p = started(withPoints(100))
        val rolled = p.exploration.actives.first().rewards
        val c = Exploration.collect(p, ExplorationSite.CAVE, 30 * minute)!!
        assertTrue(c.progress.exploration.actives.isEmpty())
        assertEquals(ExplorationSite.CAVE, c.site)
        assertEquals(3, c.rewards.values.sum())
        for ((name, n) in rolled) assertEquals(n, c.progress.inventory.count(ItemType.valueOf(name)))
        assertNull(Exploration.collect(c.progress, ExplorationSite.CAVE, 31 * minute)) // 二重に受け取れない
    }

    @Test
    fun collect_keepsExistingItems() {
        val p = started(MiniGameProgress(inventory = Inventory(explorationPoints = 100).add(ItemType.RICE, 5)))
        val c = Exploration.collect(p, ExplorationSite.CAVE, 30 * minute)!!
        assertTrue(c.progress.inventory.count(ItemType.RICE) >= 5)
    }

    @Test
    fun aNewExplorationCanStartAfterCollecting() {
        val first = started(withPoints(300))
        val collected = Exploration.collect(first, ExplorationSite.CAVE, 30 * minute)!!.progress
        assertTrue(Exploration.start(collected, ExplorationSite.MOUNTAIN, ExplorationPlan.SHORT, 40 * minute) is StartResult.Started)
    }

    // --- ログインボーナス(8.2) ---

    @Test
    fun loginBonus_isGivenOncePerDay() {
        val (a, first) = Exploration.claimLoginBonus(MiniGameProgress(), 10L)
        assertTrue(first)
        assertEquals(100, a.inventory.explorationPoints)
        assertEquals(3, a.inventory.count(ItemType.RICE)) // ごはんも 3 個補充される
        val (b, second) = Exploration.claimLoginBonus(a, 10L)
        assertFalse(second)
        assertEquals(100, b.inventory.explorationPoints)
        assertEquals(3, b.inventory.count(ItemType.RICE)) // 同じ日に 2 回は増えない
        val (c, third) = Exploration.claimLoginBonus(b, 11L)
        assertTrue(third)
        assertEquals(200, c.inventory.explorationPoints)
        assertEquals(6, c.inventory.count(ItemType.RICE))
    }

    @Test
    fun aFreshPlayerCanExploreOnceForFreeFromTheLoginBonus() {
        val (p, _) = Exploration.claimLoginBonus(MiniGameProgress(), 1L)
        assertTrue(Exploration.start(p, ExplorationSite.CAVE, ExplorationPlan.SHORT, 0L) is StartResult.Started)
    }

    @Test
    fun startingGift_isTenRice_givenOnlyOnce() {
        val (a, first) = Exploration.claimStartingGift(MiniGameProgress())
        assertTrue(first)
        assertEquals(10, a.inventory.count(ItemType.RICE))
        val (b, second) = Exploration.claimStartingGift(a)
        assertFalse(second)
        assertEquals(10, b.inventory.count(ItemType.RICE))
    }

    @Test
    fun startingGift_stacksWithTheLoginBonus() {
        val (a, _) = Exploration.claimStartingGift(MiniGameProgress())
        val (b, _) = Exploration.claimLoginBonus(a, 1L)
        assertEquals(13, b.inventory.count(ItemType.RICE)) // 10 + 3
    }

    @Test
    fun oldSavesWithoutTheGiftFlagStillLoad_andCanClaimTheGift() {
        val old = """{"inventory":{"explorationPoints":5},"exploration":{"loginBonusDay":3}}"""
        val p = MiniGameProgressCodec.decode(old)!!
        assertFalse(p.exploration.startingGiftGiven)
        assertTrue(Exploration.claimStartingGift(p).second)
    }

    @Test
    fun loginRice_followsTheConfig() {
        val (p, _) = Exploration.claimLoginBonus(MiniGameProgress(), 1L, ExplorationConfig(loginRice = 3))
        assertEquals(3, p.inventory.count(ItemType.RICE))
    }

    // --- 残り時間の表示 ---

    @Test
    fun remainingText_formatsHoursAndMinutes() {
        assertEquals("3時間30分", Exploration.remainingText(3 * hour + 30 * minute))
        assertEquals("4時間", Exploration.remainingText(4 * hour))
        assertEquals("45分", Exploration.remainingText(45 * minute))
        assertEquals("1時間", Exploration.remainingText(60 * minute))
        assertEquals("59分", Exploration.remainingText(59 * minute))
    }

    @Test
    fun remainingText_roundsUpToTheNextMinute() {
        assertEquals("1分", Exploration.remainingText(1))
        assertEquals("1分", Exploration.remainingText(60_000))
        assertEquals("2分", Exploration.remainingText(60_001))
        assertEquals("0分", Exploration.remainingText(0))
        assertEquals("0分", Exploration.remainingText(-5))
    }

    // --- 保存 ---

    @Test
    fun theExplorationSurvivesSaveAndLoad() {
        val p = started(withPoints(300), plan = ExplorationPlan.SHORT, now = 123L)
        assertEquals(p, MiniGameProgressCodec.decode(MiniGameProgressCodec.encode(p)))
        assertEquals(1, MiniGameProgressCodec.decode(MiniGameProgressCodec.encode(p))!!.exploration.actives.size)
    }

    @Test
    fun oldSavesWithoutExplorationStillLoad() {
        val old = """{"daily":{"day":1},"inventory":{"explorationPoints":250,"items":{"RICE":2}}}"""
        val p = MiniGameProgressCodec.decode(old)!!
        assertTrue(p.exploration.actives.isEmpty())
        assertEquals(250, p.inventory.explorationPoints)
    }

    // --- 所持ポイント ---

    @Test
    fun inventory_spendPoints() {
        val inv = Inventory(explorationPoints = 150)
        assertEquals(50, inv.spendPoints(100)!!.explorationPoints)
        assertNull(inv.spendPoints(151))
        assertEquals(150, inv.explorationPoints)
    }
}
