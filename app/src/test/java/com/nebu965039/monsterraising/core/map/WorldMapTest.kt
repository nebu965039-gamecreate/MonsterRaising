package com.nebu965039.monsterraising.core.map

import com.nebu965039.monsterraising.core.exploration.ExplorationOverview
import com.nebu965039.monsterraising.core.exploration.ExplorationPlan
import com.nebu965039.monsterraising.core.exploration.ExplorationSite
import com.nebu965039.monsterraising.core.exploration.ExplorationStatus
import com.nebu965039.monsterraising.core.minigame.ItemType
import com.nebu965039.monsterraising.core.minigame.MiniGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class WorldMapTest {
    private fun inProgress(site: ExplorationSite, remainingMs: Long) =
        ExplorationStatus.InProgress(site, ExplorationPlan.SHORT, remainingMs)

    private fun finished(site: ExplorationSite) =
        ExplorationStatus.Finished(site, ExplorationPlan.SHORT, mapOf(ItemType.RICE to 3))

    // --- 拠点の配置(10.4) ---

    @Test
    fun hasHome_theThreeGameBases_andTheThreeExplorationSites() {
        val ids = WorldMap.locations().map { it.id }
        assertEquals(MapLocationId.entries.toSet(), ids.toSet())
        assertEquals(7, ids.size)
        assertEquals(setOf(MapLocationId.CAVE, MapLocationId.COAST, MapLocationId.MOUNTAIN), WorldMap.locations().filter { it.kind == LocationKind.EXPLORATION }.map { it.id }.toSet())
        assertEquals(
            setOf(MapLocationId.GAME_PUZZLE, MapLocationId.GAME_CARD, MapLocationId.GAME_WALLBREAK),
            WorldMap.locations().filter { it.kind == LocationKind.GAME_BASE }.map { it.id }.toSet(),
        )
    }

    @Test
    fun everyExplorationSiteHasExactlyOneLocation() {
        for (site in ExplorationSite.entries) {
            assertEquals(1, WorldMap.locations().count { it.site == site })
            assertEquals(site, WorldMap.locations().first { it.id == WorldMap.locationOf(site) }.site)
        }
    }

    @Test
    fun namesAreUnique_andIconsAreSet() {
        val locations = WorldMap.locations()
        assertEquals(locations.size, locations.map { it.displayName }.toSet().size)
        assertTrue(locations.all { it.icon.isNotEmpty() })
    }

    @Test
    fun positionsAreInsideTheMap_andIconsDoNotOverlap() {
        val locations = WorldMap.locations()
        for (l in locations) assertTrue("${l.id} x", l.x in 0.05f..0.95f && l.y in 0.05f..0.95f)
        for (a in locations) for (b in locations) {
            if (a.id < b.id) assertTrue("${a.id} and ${b.id} are too close", hypot(a.x - b.x, a.y - b.y) >= 0.2f)
        }
    }

    // --- 未解放(10.4) ---

    @Test
    fun allLocationsAreUnlockedForNow() {
        assertTrue(WorldMap.locations().all { it.unlocked })
    }

    @Test
    fun locationsOutsideTheUnlockedSetAreLocked() {
        val locations = WorldMap.locations(unlocked = setOf(MapLocationId.HOME, MapLocationId.GAME_PUZZLE))
        assertTrue(locations.first { it.id == MapLocationId.HOME }.unlocked)
        assertFalse(locations.first { it.id == MapLocationId.CAVE }.unlocked)
        assertEquals(2, locations.count { it.unlocked })
    }

    // --- 現在地(10.4) ---

    @Test
    fun currentLocation_isHomeWhenNotExploring() {
        assertEquals(MapLocationId.HOME, WorldMap.currentLocation(ExplorationOverview(emptyList(), emptyList())))
    }

    @Test
    fun currentLocation_isTheSiteBeingExplored() {
        val o = ExplorationOverview(listOf(inProgress(ExplorationSite.COAST, 1_000)), emptyList())
        assertEquals(MapLocationId.COAST, WorldMap.currentLocation(o))
    }

    @Test
    fun currentLocation_withSeveralSites_isTheOneEndingFirst() {
        // 概要は、終わるのが早い順に並んでいる
        val o = ExplorationOverview(
            listOf(inProgress(ExplorationSite.MOUNTAIN, 500), inProgress(ExplorationSite.CAVE, 9_000)),
            emptyList(),
        )
        assertEquals(MapLocationId.MOUNTAIN, WorldMap.currentLocation(o))
    }

    @Test
    fun currentLocation_staysAtAFinishedSiteUntilTheRewardsAreCollected() {
        val o = ExplorationOverview(emptyList(), listOf(finished(ExplorationSite.CAVE)))
        assertEquals(MapLocationId.CAVE, WorldMap.currentLocation(o))
    }

    @Test
    fun currentLocation_prefersASiteStillInProgress() {
        val o = ExplorationOverview(listOf(inProgress(ExplorationSite.COAST, 1_000)), listOf(finished(ExplorationSite.CAVE)))
        assertEquals(MapLocationId.COAST, WorldMap.currentLocation(o))
    }

    // --- 遷移(10.4: 拠点に対応する画面へ) ---

    @Test
    fun tappingALocation_opensItsScreen() {
        assertEquals(Destination.Home, Destination.forLocation(MapLocationId.HOME))
        assertEquals(Destination.Game(MiniGame.PUZZLE), Destination.forLocation(MapLocationId.GAME_PUZZLE))
        assertEquals(Destination.Game(MiniGame.CARD), Destination.forLocation(MapLocationId.GAME_CARD))
        assertEquals(Destination.Game(MiniGame.WALL_BREAK), Destination.forLocation(MapLocationId.GAME_WALLBREAK))
        assertEquals(Destination.Site(ExplorationSite.CAVE), Destination.forLocation(MapLocationId.CAVE))
        assertEquals(Destination.Site(ExplorationSite.COAST), Destination.forLocation(MapLocationId.COAST))
        assertEquals(Destination.Site(ExplorationSite.MOUNTAIN), Destination.forLocation(MapLocationId.MOUNTAIN))
    }

    @Test
    fun everyLocationHasADestination() {
        for (id in MapLocationId.entries) assertTrue(Destination.forLocation(id).key.isNotEmpty())
    }

    // --- 戻る操作 ---

    @Test
    fun back_goesUpTheTree() {
        assertNull(Destination.Home.parent)
        assertEquals(Destination.Home, Destination.WorldMap.parent)
        assertEquals(Destination.WorldMap, Destination.Game(MiniGame.PUZZLE).parent)
        assertEquals(Destination.WorldMap, Destination.Site(ExplorationSite.CAVE).parent)
        assertEquals(Destination.Home, Destination.Dev.parent)
        assertEquals(Destination.Home, Destination.Dex.parent)
        assertEquals(Destination.Home, Destination.Friends.parent)
        assertEquals(Destination.Home, Destination.Items.parent)
        assertEquals(Destination.Home, Destination.Settings.parent)
    }

    @Test
    fun repeatedBack_alwaysEndsAtHome() {
        val all = listOf(Destination.Home, Destination.WorldMap, Destination.Dex, Destination.Friends, Destination.Items, Destination.Settings, Destination.Dev) +
            MiniGame.entries.map { Destination.Game(it) } + ExplorationSite.entries.map { Destination.Site(it) }
        for (start in all) {
            var d: Destination = start
            var guard = 0
            while (d.parent != null && guard++ < 10) d = d.parent!!
            assertEquals("$start", Destination.Home, d)
        }
    }

    // --- 保存 ---

    @Test
    fun keys_roundTrip() {
        val all = listOf(Destination.Home, Destination.WorldMap, Destination.Dex, Destination.Friends, Destination.Items, Destination.Settings, Destination.Dev) +
            MiniGame.entries.map { Destination.Game(it) } + ExplorationSite.entries.map { Destination.Site(it) }
        for (d in all) assertEquals(d, Destination.fromKey(d.key))
        assertEquals(all.size, all.map { it.key }.toSet().size) // キーは重複しない
    }

    @Test
    fun unknownKeys_areRejected() {
        assertNull(Destination.fromKey(""))
        assertNull(Destination.fromKey("site:NOWHERE"))
        assertNull(Destination.fromKey("game:CHESS"))
        assertNull(Destination.fromKey("garbage"))
    }
}
