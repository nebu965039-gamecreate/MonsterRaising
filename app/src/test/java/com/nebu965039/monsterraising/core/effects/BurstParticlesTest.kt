package com.nebu965039.monsterraising.core.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstParticlesTest {
    @Test
    fun atTheStart_everyFragmentIsAtTheCenter_fullyOpaque() {
        val f = BurstParticles.fragments(seed = 1, elapsedMs = 0)
        assertEquals(BurstParticles.DEFAULT_COUNT, f.size)
        for (p in f) {
            assertEquals(0f, p.dx, 1e-6f)
            assertEquals(0f, p.dy, 1e-6f)
            assertEquals(1f, p.alpha, 1e-6f)
            assertTrue(p.size > 0f)
        }
    }

    @Test
    fun beforeTheStartAndAfterTheEnd_thereAreNoFragments() {
        assertTrue(BurstParticles.fragments(1, -1).isEmpty())
        assertTrue(BurstParticles.fragments(1, BurstParticles.DURATION_MS).isEmpty())
        assertTrue(BurstParticles.fragments(1, 5_000).isEmpty())
    }

    @Test
    fun fragmentsFlyOutInBothDirections() {
        val f = BurstParticles.fragments(seed = 7, elapsedMs = 200)
        assertTrue(f.any { it.dx > 0.05f })
        assertTrue(f.any { it.dx < -0.05f })
        assertTrue(f.any { it.dy < 0f }) // やや上向きにも飛ぶ
    }

    @Test
    fun theyFade_andGetSmaller_overTime() {
        val early = BurstParticles.fragments(3, 100)
        val late = BurstParticles.fragments(3, 600)
        for (i in early.indices) {
            assertTrue(late[i].alpha < early[i].alpha)
            assertTrue(late[i].size < early[i].size)
        }
    }

    @Test
    fun gravityPullsThemDownEventually() {
        val f = BurstParticles.fragments(seed = 5, elapsedMs = 690)
        assertTrue(f.any { it.dy > 0f })
    }

    @Test
    fun sameSeedGivesTheSameMotion_differentSeedsDiffer() {
        assertEquals(BurstParticles.fragments(9, 300), BurstParticles.fragments(9, 300))
        assertNotEquals(BurstParticles.fragments(9, 300), BurstParticles.fragments(10, 300))
    }

    @Test
    fun aFragmentKeepsItsIdentityAcrossFrames() {
        // 同じ種なら i 番目の破片は、時間が進んでも同じ初速で動き続ける(x は時間に比例する)
        val a = BurstParticles.fragments(4, 100)
        val b = BurstParticles.fragments(4, 200)
        for (i in a.indices) assertEquals(a[i].dx * 2f, b[i].dx, 1e-4f)
    }

    @Test
    fun alphaStaysWithinRange() {
        for (ms in 0 until BurstParticles.DURATION_MS step 25) {
            for (p in BurstParticles.fragments(2, ms)) assertTrue(p.alpha in 0f..1f)
        }
    }
}
