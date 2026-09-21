package com.nebu965039.monsterraising.core.sprite

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 差分画像方式(基本設計書3章)のキャラクター定義。UI・Android 非依存。 */
@Serializable
data class SpriteDefinition(
    val id: String,
    val size: Int,
    /** フレームキー → 画像の相対パス */
    val frames: Map<String, String>,
    val animations: Map<String, AnimationDef>,
)

@Serializable
data class AnimationDef(
    val loop: Boolean = true,
    /** loop=false の終了後に遷移するアニメーション名 */
    val next: String? = null,
    val steps: List<Step>,
)

@Serializable
data class Step(val frame: String, val ms: Long) {
    init {
        require(ms > 0) { "ms must be positive: $ms" }
    }
}

class SpriteDefinitionException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SpriteDefinitionParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** JSON を読み込み、参照整合性(フレーム・next の存在など)を検証して返す。 */
    fun parse(text: String): SpriteDefinition {
        val def = try {
            json.decodeFromString<SpriteDefinition>(text)
        } catch (e: SerializationException) {
            throw SpriteDefinitionException("invalid sprite json: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw SpriteDefinitionException("invalid sprite json: ${e.message}", e)
        }
        validate(def)
        return def
    }

    private fun validate(def: SpriteDefinition) {
        fun fail(msg: String): Nothing = throw SpriteDefinitionException(msg)
        if (def.size <= 0) fail("size must be positive: ${def.size}")
        if (def.animations.isEmpty()) fail("no animations")
        for ((name, anim) in def.animations) {
            if (anim.steps.isEmpty()) fail("animation '$name' has no steps")
            for (step in anim.steps) {
                if (step.frame !in def.frames) fail("animation '$name' uses unknown frame '${step.frame}'")
            }
            val next = anim.next
            if (next != null) {
                if (next !in def.animations) fail("animation '$name' has unknown next '$next'")
                if (anim.loop) fail("animation '$name' is looping but has next")
            }
        }
    }
}
