package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.PetStats
import com.nebu965039.monsterraising.core.pet.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElixirTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val week = 7 * day

    private fun mature(last: Long, extension: Long = 0L) =
        PetState(PetStats(100.0, 100.0, 50.0, 200.0, 100.0), Stage.MATURE, 0L, last, lifespanExtensionMs = extension)

    private fun withElixirs(n: Int) = MiniGameProgress(inventory = Inventory().add(ItemType.ELIXIR, n))

    @Test
    fun using_consumesOneAndExtendsTheLifespanByAWeek() {
        val now = 10 * day
        val r = Elixir.use(withElixirs(2), mature(last = now), now) as ElixirUse.Used
        assertEquals(1, r.progress.inventory.count(ItemType.ELIXIR))
        assertEquals(week, r.pet.lifespanExtensionMs)
        assertEquals(14 * day + week, r.pet.lifespanEndMs())
    }

    @Test
    fun severalElixirs_stackUp() {
        val now = 10 * day
        var progress = withElixirs(3)
        var pet = mature(last = now)
        repeat(3) {
            val r = Elixir.use(progress, pet, now) as ElixirUse.Used
            progress = r.progress
            pet = r.pet
        }
        assertEquals(3 * week, pet.lifespanExtensionMs)
        assertEquals(0, progress.inventory.count(ItemType.ELIXIR))
    }

    @Test
    fun withoutAnElixir_nothingHappens() {
        val now = 10 * day
        assertEquals(ElixirUse.NoElixir, Elixir.use(MiniGameProgress(), mature(last = now), now))
    }

    @Test
    fun notMature_doesNotConsumeTheElixir() {
        val infant = PetState(PetStats(100.0, 100.0, 50.0), Stage.INFANT, 0L, 0L)
        val r = Elixir.use(withElixirs(1), infant, 1 * hour)
        assertTrue(r is ElixirUse.NotMature)
    }

    @Test
    fun afterTheLifespanEnded_itIsTooLate_andTheElixirIsKept() {
        // 寿命(2 週間)を過ぎてから使おうとすると、経過を反映した時点で卵になっているので使えない
        val r = Elixir.use(withElixirs(1), mature(last = 14 * day - hour), 14 * day + hour)
        val notMature = r as ElixirUse.NotMature
        assertEquals(Stage.EGG, notMature.pet.stage)
    }
}
