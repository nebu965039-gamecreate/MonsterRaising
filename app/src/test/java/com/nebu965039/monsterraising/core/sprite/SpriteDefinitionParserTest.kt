package com.nebu965039.monsterraising.core.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpriteDefinitionParserTest {
    private fun json(animations: String, frames: String = """{ "a": "a.png" }""", size: Int = 48) =
        """{ "id": "t", "size": $size, "frames": $frames, "animations": $animations }"""

    private fun assertRejected(text: String, messagePart: String) {
        try {
            SpriteDefinitionParser.parse(text)
            throw AssertionError("expected SpriteDefinitionException")
        } catch (e: SpriteDefinitionException) {
            assertTrue("message was: ${e.message}", e.message!!.contains(messagePart))
        }
    }

    @Test
    fun parsesMinimalDefinition() {
        val def = SpriteDefinitionParser.parse(json("""{ "idle": { "steps": [ { "frame": "a", "ms": 100 } ] } }"""))
        assertEquals(48, def.size)
        assertTrue(def.animations.getValue("idle").loop) // loop の既定は true
    }

    @Test
    fun rejectsUnknownFrame() =
        assertRejected(json("""{ "idle": { "steps": [ { "frame": "zzz", "ms": 100 } ] } }"""), "unknown frame")

    @Test
    fun rejectsNonPositiveMs() =
        assertRejected(json("""{ "idle": { "steps": [ { "frame": "a", "ms": 0 } ] } }"""), "ms must be positive")

    @Test
    fun rejectsEmptySteps() =
        assertRejected(json("""{ "idle": { "steps": [] } }"""), "no steps")

    @Test
    fun rejectsUnknownNext() =
        assertRejected(
            json("""{ "x": { "loop": false, "next": "nope", "steps": [ { "frame": "a", "ms": 100 } ] } }"""),
            "unknown next",
        )

    @Test
    fun rejectsLoopWithNext() =
        assertRejected(
            json("""{ "x": { "loop": true, "next": "x", "steps": [ { "frame": "a", "ms": 100 } ] } }"""),
            "looping but has next",
        )

    @Test
    fun rejectsMalformedJson() = assertRejected("{ not json", "invalid sprite json")

    @Test
    fun rejectsNonPositiveSize() =
        assertRejected(json("""{ "idle": { "steps": [ { "frame": "a", "ms": 100 } ] } }""", size = 0), "size")

    /** 同梱の fox.json が正しく、参照する画像がすべて 48x48 PNG として存在すること。 */
    @Test
    fun bundledFoxDefinition_isValidAndFilesExist() {
        val dir = File("src/main/assets/characters/fox")
        val def = SpriteDefinitionParser.parse(File(dir, "fox.json").readText())
        for ((key, path) in def.frames) {
            val f = File(dir, path)
            assertTrue("missing frame file for '$key': $f", f.exists())
            val img = javax.imageio.ImageIO.read(f)
            assertEquals("width of $path", def.size, img.width)
            assertEquals("height of $path", def.size, img.height)
        }
    }
}
