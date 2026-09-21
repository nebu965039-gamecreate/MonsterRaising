package com.nebu965039.monsterraising.core.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteTimelineTest {
    private val def = SpriteDefinitionParser.parse(
        """
        {
          "id": "t", "size": 48,
          "frames": { "a": "a.png", "b": "b.png", "c": "c.png" },
          "animations": {
            "idle": { "loop": true, "steps": [ { "frame": "a", "ms": 100 }, { "frame": "b", "ms": 200 } ] },
            "once": { "loop": false, "steps": [ { "frame": "b", "ms": 100 }, { "frame": "c", "ms": 100 } ] },
            "chain": { "loop": false, "next": "idle", "steps": [ { "frame": "c", "ms": 100 } ] }
          }
        }
        """.trimIndent(),
    )

    @Test
    fun loop_switchesFramesAtBoundaries() {
        assertEquals("a", SpriteTimeline.resolve(def, "idle", 0).frameKey)
        assertEquals("a", SpriteTimeline.resolve(def, "idle", 99).frameKey)
        assertEquals("b", SpriteTimeline.resolve(def, "idle", 100).frameKey)
        assertEquals("b", SpriteTimeline.resolve(def, "idle", 299).frameKey)
    }

    @Test
    fun loop_wrapsAround() {
        assertEquals("a", SpriteTimeline.resolve(def, "idle", 300).frameKey)
        assertEquals("b", SpriteTimeline.resolve(def, "idle", 3_100).frameKey)
        assertFalse(SpriteTimeline.resolve(def, "idle", 3_100).finished)
    }

    @Test
    fun nonLoop_holdsLastFrameAndFinishes() {
        assertEquals("c", SpriteTimeline.resolve(def, "once", 100).frameKey)
        val held = SpriteTimeline.resolve(def, "once", 10_000)
        assertEquals("c", held.frameKey)
        assertTrue(held.finished)
        assertEquals("once", held.animation)
    }

    @Test
    fun nonLoop_movesToNextAnimation() {
        val f = SpriteTimeline.resolve(def, "chain", 100 + 100)
        assertEquals("idle", f.animation)
        assertEquals("b", f.frameKey)
        assertFalse(f.finished)
    }

    @Test
    fun stepOffsets_areCarriedToFrame() {
        val bob = SpriteDefinitionParser.parse(
            """
            {
              "id": "t", "size": 48, "frames": { "a": "a.png" },
              "animations": {
                "idle": { "steps": [ { "frame": "a", "ms": 100 }, { "frame": "a", "ms": 100, "dx": 1, "dy": -1 } ] }
              }
            }
            """.trimIndent(),
        )
        val rest = SpriteTimeline.resolve(bob, "idle", 0)
        assertEquals(0, rest.dx)
        assertEquals(0, rest.dy)
        val up = SpriteTimeline.resolve(bob, "idle", 100)
        assertEquals(1, up.dx)
        assertEquals(-1, up.dy)
        assertEquals(-1, SpriteTimeline.resolve(bob, "idle", 300).dy) // 周回後も同じ位相
    }

    @Test
    fun negativeElapsed_isTreatedAsZero() {
        assertEquals("a", SpriteTimeline.resolve(def, "idle", -50).frameKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownAnimation_throws() {
        SpriteTimeline.resolve(def, "nope", 0)
    }

    @Test
    fun integerScale_floorsAndHasMinimumOne() {
        assertEquals(5, SpriteTimeline.integerScale(240, 48))
        assertEquals(5, SpriteTimeline.integerScale(287, 48))
        assertEquals(1, SpriteTimeline.integerScale(30, 48))
        assertEquals(1, SpriteTimeline.integerScale(0, 48))
    }
}
