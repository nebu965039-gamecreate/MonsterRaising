package com.nebu965039.monsterraising.core.dex

import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.PetStats
import com.nebu965039.monsterraising.core.pet.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexTest {
    private fun pet(stage: Stage, intellect: Double = 0.0, strength: Double = 0.0) =
        PetState(PetStats(100.0, 100.0, 50.0, intellect, strength), stage, 0L, 0L)

    // --- 図鑑の一覧(10.5) ---

    @Test
    fun everySpeciesHasAName_aCondition_andAUniqueIdAndNumber() {
        assertEquals(Dex.species.size, Dex.species.map { it.id }.toSet().size)
        assertEquals(Dex.species.size, Dex.species.map { it.number }.toSet().size)
        assertTrue(Dex.species.all { it.displayName.isNotBlank() && it.condition.isNotBlank() })
    }

    @Test
    fun listsEveryStage_theNormalRoutes_theSpecialRoutes_andNoZero() {
        val ids = Dex.species.map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("egg", "infant", "growth1", "growth2")))
        assertTrue(ids.containsAll(listOf("mature_intellect", "mature_strength")))
        assertTrue(ids.containsAll(listOf("mature_weather", "mature_item", "mature_friend_1", "mature_friend_2")))
        assertEquals(0, Dex.find(Dex.NO_ZERO)!!.number)
    }

    @Test
    fun friendRoutesMentionTheDesignThresholds() {
        assertTrue(Dex.find("mature_friend_1")!!.condition.contains("10,000P"))
        assertTrue(Dex.find("mature_friend_2")!!.condition.contains("100,000P"))
    }

    // --- 発見(10.5) ---

    @Test
    fun anEgg_discoversOnlyTheEgg() {
        assertEquals(setOf("egg"), Dex.discoveredBy(pet(Stage.EGG)))
    }

    @Test
    fun eachStage_includesEveryStagePassedThrough() {
        assertEquals(setOf("egg", "infant"), Dex.discoveredBy(pet(Stage.INFANT)))
        assertEquals(setOf("egg", "infant", "growth1"), Dex.discoveredBy(pet(Stage.GROWTH_1)))
        assertEquals(setOf("egg", "infant", "growth1", "growth2"), Dex.discoveredBy(pet(Stage.GROWTH_2)))
    }

    @Test
    fun matureRoute_followsTheEffectiveness() {
        val i = Dex.discoveredBy(pet(Stage.MATURE, intellect = 50.0, strength = 10.0))
        assertTrue("mature_intellect" in i && "mature_strength" !in i)
        val s = Dex.discoveredBy(pet(Stage.MATURE, intellect = 10.0, strength = 50.0))
        assertTrue("mature_strength" in s && "mature_intellect" !in s)
        assertTrue("mature_intellect" in Dex.discoveredBy(pet(Stage.MATURE, 20.0, 20.0))) // 同数は知力ルート扱い
    }

    @Test
    fun specialRoutesAndNoZero_areNotDiscoveredYet() {
        val all = Dex.discoveredBy(pet(Stage.MATURE, 100.0, 1.0))
        assertFalse("mature_weather" in all || "mature_item" in all || "mature_friend_1" in all || Dex.NO_ZERO in all)
    }

    // --- 記録 ---

    @Test
    fun records_accumulateAcrossGenerations() {
        var r = DexRecords()
        r = r.discover(Dex.discoveredBy(pet(Stage.MATURE, 50.0, 10.0)))
        assertEquals(5, r.count) // egg, infant, growth1, growth2, mature_intellect
        // 次の世代は卵からやり直しても、発見済みは消えない
        r = r.discover(Dex.discoveredBy(pet(Stage.EGG)))
        assertTrue(r.isDiscovered("mature_intellect"))
    }

    @Test
    fun records_ignoreUnknownIds() {
        val r = DexRecords().discover(setOf("egg", "nonsense"))
        assertEquals(1, r.count)
        assertFalse(r.isDiscovered("nonsense"))
    }

    @Test
    fun records_countAndTotal() {
        val r = DexRecords().discover(setOf("egg", "infant"))
        assertEquals(2, r.count)
        assertEquals(Dex.species.size, r.total)
    }

    @Test
    fun codec_roundTripsAndRejectsGarbage() {
        val r = DexRecords().discover(setOf("egg", "growth1"))
        assertEquals(r, DexRecordsCodec.decode(DexRecordsCodec.encode(r)))
        assertNull(DexRecordsCodec.decode("{ broken"))
        assertEquals(DexRecords(), DexRecordsCodec.decode("{}"))
    }
}
