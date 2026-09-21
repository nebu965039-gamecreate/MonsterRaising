package com.nebu965039.monsterraising.core.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StainsTest {
    private val w = 400f
    private val h = 400f

    /** 汚れの位置(候補地点の 0 番)へ指を動かす */
    private fun StainField.onFirst(): List<Int> {
        val s = stains.first()
        return touch(s.x * w, s.y * h, w, h)
    }

    private fun StainField.away() = touch(0f, 0f, w, h)

    private fun StainField.oneStroke() = run { onFirst().also { away() } }

    // --- 汚れの数(10.2.2) ---

    @Test
    fun stainCount_followsTheDesignTable() {
        assertEquals(0, StainRules.countFor(100.0))
        assertEquals(0, StainRules.countFor(80.0))
        assertEquals(1, StainRules.countFor(79.9))
        assertEquals(1, StainRules.countFor(60.0))
        assertEquals(2, StainRules.countFor(59.9))
        assertEquals(2, StainRules.countFor(40.0))
        assertEquals(3, StainRules.countFor(39.9))
        assertEquals(3, StainRules.countFor(20.0))
        assertEquals(4, StainRules.countFor(19.9))
        assertEquals(4, StainRules.countFor(0.1))
        assertEquals(5, StainRules.countFor(0.0))
    }

    @Test
    fun fiveStrokesRemoveAStain() {
        assertEquals(5, StainRules.STROKES_PER_STAIN)
    }

    // --- 表示する汚れ ---

    @Test
    fun sync_addsStainsAtDistinctCandidatePoints() {
        val f = StainField()
        f.sync(3)
        assertEquals(listOf(0, 1, 2), f.stains.map { it.slot })
        assertEquals(3, f.stains.map { it.x to it.y }.toSet().size)
    }

    @Test
    fun sync_neverExceedsTheCandidates_andNeverGoesNegative() {
        val f = StainField()
        f.sync(99)
        assertEquals(5, f.stains.size)
        f.sync(-3)
        assertTrue(f.isClean)
    }

    @Test
    fun sync_removesTheHighestSlotsFirst_andKeepsStrokesOfTheRest() {
        val f = StainField()
        f.sync(3)
        f.oneStroke()
        f.oneStroke()
        f.sync(2)
        assertEquals(listOf(0, 1), f.stains.map { it.slot })
        assertEquals(2, f.stains.first { it.slot == 0 }.strokes) // 持ち越し
    }

    @Test
    fun sync_addsNewStainsWithoutTouchingExistingOnes() {
        val f = StainField()
        f.sync(1)
        f.oneStroke()
        f.sync(3)
        assertEquals(3, f.stains.size)
        assertEquals(1, f.stains.first { it.slot == 0 }.strokes)
        assertEquals(0, f.stains.first { it.slot == 2 }.strokes)
    }

    // --- なでる(10.2.2) ---

    @Test
    fun eachPassOverAStainAddsOneStroke() {
        val f = StainField()
        f.sync(1)
        f.oneStroke()
        assertEquals(1, f.stains.first().strokes)
        f.oneStroke()
        assertEquals(2, f.stains.first().strokes)
    }

    @Test
    fun stayingOnAStainInOnePassCountsOnce() {
        val f = StainField()
        f.sync(1)
        val s = f.stains.first()
        repeat(10) { f.touch(s.x * w + it, s.y * h, w, h) }
        assertEquals(1, f.stains.first().strokes)
    }

    @Test
    fun theFifthPassRemovesTheStain() {
        val f = StainField()
        f.sync(1)
        repeat(4) { assertTrue(f.oneStroke().isEmpty()) }
        assertEquals(listOf(0), f.oneStroke())
        assertTrue(f.isClean)
    }

    @Test
    fun touchingFarFromAStainDoesNothing() {
        val f = StainField()
        f.sync(2)
        f.touch(w / 2, 5f, w, h)
        assertTrue(f.stains.all { it.strokes == 0 })
    }

    @Test
    fun aPassCanTouchSeveralStainsAtOnce_whenTheyOverlapTheFinger() {
        val f = StainField(listOf(0.5f to 0.5f, 0.52f to 0.5f))
        f.sync(2)
        f.touch(0.51f * w, 0.5f * h, w, h)
        assertEquals(listOf(1, 1), f.stains.map { it.strokes })
    }

    @Test
    fun aNewSwipeCountsAgainEvenOnTheSameSpot() {
        val f = StainField()
        f.sync(1)
        val s = f.stains.first()
        f.touch(s.x * w, s.y * h, w, h)
        f.endStroke()
        f.touch(s.x * w, s.y * h, w, h)
        assertEquals(2, f.stains.first().strokes)
    }

    @Test
    fun removingOneStainLeavesTheOthers() {
        val f = StainField()
        f.sync(2)
        val first = f.stains.first()
        repeat(5) {
            f.touch(first.x * w, first.y * h, w, h)
            f.away()
        }
        assertEquals(listOf(1), f.stains.map { it.slot })
    }

    // --- 汚れ以外の場所のタップ ---

    @Test
    fun hasStainNear_tellsWhetherATapHitsAStain() {
        val f = StainField()
        f.sync(1)
        val s = f.stains.first()
        assertTrue(f.hasStainNear(s.x * w, s.y * h, w, h))
        assertFalse(f.hasStainNear(w / 2, 5f, w, h))
    }

    // --- 全部きれいにする ---

    @Test
    fun cleaningEverythingEmptiesTheField() {
        val f = StainField()
        f.sync(StainRules.countFor(0.0))
        assertEquals(5, f.stains.size)
        var guard = 0
        while (!f.isClean && guard++ < 100) f.oneStroke()
        assertTrue(f.isClean)
        assertEquals(25, guard) // 5 箇所 × 5 回
    }
}
