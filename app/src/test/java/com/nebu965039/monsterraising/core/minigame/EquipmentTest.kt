package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.core.pet.PetConfig
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.PetStats
import com.nebu965039.monsterraising.core.pet.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentTest {
    private val hour = 3_600_000L

    private fun owning(vararg items: ItemType) = items.fold(Inventory()) { inv, item -> inv.add(item, 1) }

    // --- カタログ(8.4) ---

    @Test
    fun catalog_hasTheSixEquipmentsWithTheirEffects() {
        assertEquals(6, Equipment.all().size)
        assertEquals(EquipEffect(strengthGain = 0.05), Equipment.effectOf(ItemType.EQUIP_STRENGTH))
        assertEquals(EquipEffect(intellectGain = 0.05), Equipment.effectOf(ItemType.EQUIP_INTELLECT))
        assertEquals(EquipEffect(satietyDecayCut = 0.05), Equipment.effectOf(ItemType.EQUIP_SATIETY))
        assertEquals(EquipEffect(cleanlinessDecayCut = 0.05), Equipment.effectOf(ItemType.EQUIP_CLEANLINESS))
        assertEquals(EquipEffect(intellectGain = 0.025, strengthGain = 0.025), Equipment.effectOf(ItemType.EQUIP_BALANCE_EFFECT))
        assertEquals(EquipEffect(satietyDecayCut = 0.025, cleanlinessDecayCut = 0.025), Equipment.effectOf(ItemType.EQUIP_BALANCE_CARE))
    }

    @Test
    fun nonEquipmentItems_areNotEquipment() {
        assertFalse(Equipment.isEquipment(ItemType.RICE))
        assertFalse(Equipment.isEquipment(ItemType.ELIXIR))
        assertNull(Equipment.slotOf(ItemType.MINIGAME_TICKET))
    }

    @Test
    fun describe_showsThePercentages() {
        assertEquals("筋力系有効度 +5%", Equipment.describe(ItemType.EQUIP_STRENGTH))
        assertEquals("筋力系有効度 +2.5%、知力系有効度 +2.5%", Equipment.describe(ItemType.EQUIP_BALANCE_EFFECT))
        assertEquals("満腹度の減少速度 -2.5%、清潔度の減少速度 -2.5%", Equipment.describe(ItemType.EQUIP_BALANCE_CARE))
    }

    // --- 装着 ---

    @Test
    fun equip_requiresOwnership() {
        assertNull(Inventory().equip(ItemType.EQUIP_STRENGTH))
        assertNull(owning(ItemType.RICE).equip(ItemType.RICE)) // 装備でないものは装着できない
        assertTrue(owning(ItemType.EQUIP_STRENGTH).equip(ItemType.EQUIP_STRENGTH)!!.isEquipped(ItemType.EQUIP_STRENGTH))
    }

    @Test
    fun equipping_replacesTheItemInTheSameSlot() {
        val inv = owning(ItemType.EQUIP_STRENGTH, ItemType.EQUIP_INTELLECT)
            .equip(ItemType.EQUIP_STRENGTH)!!.equip(ItemType.EQUIP_INTELLECT)!!
        assertEquals(listOf(ItemType.EQUIP_INTELLECT), inv.equippedItems())
    }

    @Test
    fun differentSlots_canBeWornTogether() {
        val inv = owning(ItemType.EQUIP_STRENGTH, ItemType.EQUIP_SATIETY)
            .equip(ItemType.EQUIP_STRENGTH)!!.equip(ItemType.EQUIP_SATIETY)!!
        assertEquals(2, inv.equippedItems().size)
        val e = inv.equipEffect()
        assertEquals(0.05, e.strengthGain, 1e-9)
        assertEquals(0.05, e.satietyDecayCut, 1e-9)
    }

    @Test
    fun unequip_removesTheEffect() {
        val inv = owning(ItemType.EQUIP_STRENGTH).equip(ItemType.EQUIP_STRENGTH)!!.unequip(ItemType.EQUIP_STRENGTH)
        assertTrue(inv.equippedItems().isEmpty())
        assertEquals(EquipEffect(), inv.equipEffect())
    }

    @Test
    fun equipment_isNotConsumedAndSurvivesSerialization() {
        val inv = owning(ItemType.EQUIP_STRENGTH).equip(ItemType.EQUIP_STRENGTH)!!
        val p = MiniGameProgress(inventory = inv)
        val restored = MiniGameProgressCodec.decode(MiniGameProgressCodec.encode(p))!!
        assertTrue(restored.inventory.isEquipped(ItemType.EQUIP_STRENGTH))
        assertEquals(1, restored.inventory.count(ItemType.EQUIP_STRENGTH))
    }

    @Test
    fun oldSavedData_withoutEquippedField_stillLoads() {
        val p = MiniGameProgressCodec.decode("""{"inventory":{"explorationPoints":5}}""")!!
        assertTrue(p.inventory.equippedItems().isEmpty())
    }

    // --- 有効度の増加への上乗せ ---

    private fun settle(p: MiniGameProgress, gains: MinigameGains) =
        p.settle(MiniGame.PUZZLE, 1L, RewardTier.BEGINNER, cleared = false, firstClear = false, gains = gains)

    @Test
    fun effectEquipment_boostsTheAppliedGains() {
        val p = MiniGameProgress(inventory = owning(ItemType.EQUIP_STRENGTH).equip(ItemType.EQUIP_STRENGTH)!!)
        val s = settle(p, MinigameGains(intellect = 20.0, strength = 20.0, mood = 10.0))
        assertEquals(20.0, s.appliedGains.intellect, 1e-9) // 知力は対象外
        assertEquals(21.0, s.appliedGains.strength, 1e-9) // 筋力 +5%
        assertEquals(10.0, s.appliedGains.mood, 1e-9) // 機嫌は対象外
    }

    @Test
    fun balanceEquipment_boostsBothBy2point5Percent() {
        val p = MiniGameProgress(inventory = owning(ItemType.EQUIP_BALANCE_EFFECT).equip(ItemType.EQUIP_BALANCE_EFFECT)!!)
        val s = settle(p, MinigameGains(40.0, 40.0, 0.0))
        assertEquals(41.0, s.appliedGains.intellect, 1e-9)
        assertEquals(41.0, s.appliedGains.strength, 1e-9)
    }

    @Test
    fun theDailyCap_countsTheBaseAmount_notTheBoostedOne() {
        val p = MiniGameProgress(inventory = owning(ItemType.EQUIP_STRENGTH).equip(ItemType.EQUIP_STRENGTH)!!)
        val s = settle(p, MinigameGains(0.0, 100.0, 0.0)) // 上限 60 で削られてから上乗せ
        assertEquals(63.0, s.appliedGains.strength, 1e-9)
        assertEquals(60.0, s.progress.daily.strengthGained, 1e-9)
        assertTrue(s.capped)
    }

    @Test
    fun aCareEquipment_doesNotChangeTheGains() {
        val p = MiniGameProgress(inventory = owning(ItemType.EQUIP_SATIETY).equip(ItemType.EQUIP_SATIETY)!!)
        assertEquals(20.0, settle(p, MinigameGains(20.0, 20.0, 0.0)).appliedGains.strength, 1e-9)
    }

    @Test
    fun withoutEquipment_gainsAreUnchanged() {
        val s = settle(MiniGameProgress(), MinigameGains(12.0, 7.0, 10.0))
        assertEquals(MinigameGains(12.0, 7.0, 10.0), s.appliedGains)
    }

    // --- 満腹度・清潔度の減少速度 ---

    @Test
    fun careEquipment_slowsTheDecay() {
        val config = Equipment.applyTo(PetConfig(), EquipEffect(satietyDecayCut = 0.05, cleanlinessDecayCut = 0.025))
        assertEquals(5.7, config.satietyDecayPerHour, 1e-9)
        assertEquals(5.85, config.cleanlinessDecayPerHour, 1e-9)
        val s = PetSimulator.advance(
            PetState(PetStats(100.0, 100.0, 50.0), Stage.INFANT, 0L, 0L), 10 * hour, config,
        )
        assertEquals(43.0, s.stats.satiety, 1e-9) // 通常なら 40
        assertEquals(41.5, s.stats.cleanliness, 1e-9) // 通常なら 40
    }

    @Test
    fun slowedDecay_stillReachesZeroWithinTheCap() {
        // 24 時間上限の方針(上限より前に 0 へ届く)を、最大の軽減(5%)でも保てている
        val slowest = Equipment.applyTo(PetConfig(), EquipEffect(satietyDecayCut = 0.05, cleanlinessDecayCut = 0.05))
        assertTrue(100.0 / slowest.satietyDecayPerHour < slowest.decayCapHours)
        assertTrue(100.0 / slowest.cleanlinessDecayPerHour < slowest.decayCapHours)
    }

    @Test
    fun noEffect_leavesTheConfigAsItIs() {
        assertEquals(PetConfig(), Equipment.applyTo(PetConfig(), EquipEffect()))
    }

    // --- 装着中の装備が、所持数 0 になったら無効(古い保存データの保険) ---

    @Test
    fun anEquippedItemThatIsNoLongerOwned_hasNoEffect() {
        val inv = Inventory(equipped = setOf(ItemType.EQUIP_STRENGTH.name))
        assertTrue(inv.equippedItems().isEmpty())
        assertEquals(EquipEffect(), inv.equipEffect())
    }

}
