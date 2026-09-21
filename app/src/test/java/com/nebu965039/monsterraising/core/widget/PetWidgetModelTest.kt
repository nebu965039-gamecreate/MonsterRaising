package com.nebu965039.monsterraising.core.widget

import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.PetStats
import com.nebu965039.monsterraising.core.pet.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetWidgetModelTest {
    private fun model(stats: PetStats, stage: Stage = Stage.INFANT, generation: Int = 1) =
        PetWidgetModel.of(PetState(stats, stage, 0L, 0L, generation = generation))

    @Test
    fun frame_followsAppearanceRules() {
        assertEquals("normal", model(PetStats(100.0, 100.0, 80.0)).frameKey)
        assertEquals("sad", model(PetStats(100.0, 100.0, 30.0)).frameKey)
        assertEquals("sad", model(PetStats(30.0, 100.0, 80.0)).frameKey)
        assertEquals("dirty", model(PetStats(100.0, 0.0, 80.0)).frameKey)
    }

    @Test
    fun dots_roundToTenSteps() {
        assertEquals(0, PetWidgetModel.dots(0.0))
        assertEquals(0, PetWidgetModel.dots(4.9))
        assertEquals(1, PetWidgetModel.dots(5.0))
        assertEquals(5, PetWidgetModel.dots(50.0))
        assertEquals(10, PetWidgetModel.dots(100.0))
        assertEquals(10, PetWidgetModel.dots(120.0))
        assertEquals(0, PetWidgetModel.dots(-3.0))
    }

    @Test
    fun gaugesAreShownAsDots() {
        val m = model(PetStats(76.0, 33.0, 50.0))
        assertEquals(8, m.satietyDots)
        assertEquals(3, m.cleanlinessDots)
        assertEquals(5, m.moodDots)
        assertFalse(m.isEgg)
    }

    @Test
    fun egg_hasNoGaugesAndUsesTheNormalFrame() {
        val m = model(PetStats(0.0, 0.0, 0.0), stage = Stage.EGG)
        assertTrue(m.isEgg)
        assertEquals("normal", m.frameKey)
        assertNull(m.satietyDots)
        assertNull(m.cleanlinessDots)
        assertNull(m.moodDots)
    }

    @Test
    fun explorationNote_isPassedThrough_andNullByDefault() {
        val state = PetState(PetStats(100.0, 100.0, 50.0), Stage.INFANT, 0L, 0L)
        assertNull(PetWidgetModel.of(state).explorationNote)
        assertEquals("探索中(残り 30分)", PetWidgetModel.of(state, explorationNote = "探索中(残り 30分)").explorationNote)
        assertEquals("探索完了!", PetWidgetModel.of(state, explorationNote = "探索完了!").explorationNote)
    }

    @Test
    fun feedButton_needsRiceAndAHatchedPet() {
        val infant = PetState(PetStats(100.0, 100.0, 50.0), Stage.INFANT, 0L, 0L)
        val egg = PetState(PetStats(100.0, 100.0, 50.0), Stage.EGG, 0L, 0L)
        assertTrue(PetWidgetModel.of(infant, riceCount = 3).canFeed)
        assertEquals(3, PetWidgetModel.of(infant, riceCount = 3).riceCount)
        assertFalse(PetWidgetModel.of(infant, riceCount = 0).canFeed) // ごはんがなければ押せない
        assertFalse(PetWidgetModel.of(egg, riceCount = 3).canFeed) // 卵には餌をあげられない
    }

    @Test
    fun generationAndStageArePassedThrough() {
        val m = model(PetStats(100.0, 100.0, 50.0), stage = Stage.GROWTH_2, generation = 3)
        assertEquals(3, m.generation)
        assertEquals(Stage.GROWTH_2, m.stage)
    }
}
