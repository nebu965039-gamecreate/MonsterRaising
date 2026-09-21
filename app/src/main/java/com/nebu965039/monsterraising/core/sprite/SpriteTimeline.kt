package com.nebu965039.monsterraising.core.sprite

/** ある時刻に表示すべき状態。 */
data class SpriteFrame(
    /** 実際に再生中のアニメーション名(next 遷移後は遷移先) */
    val animation: String,
    val frameKey: String,
    /** 非ループで最終フレームに到達し、以降は保持される状態 */
    val finished: Boolean,
)

object SpriteTimeline {
    /**
     * [animation] を開始してから [elapsedMs] 経過した時点の表示フレームを返す。
     * loop=true は周回、loop=false は next があれば遷移し、なければ最終フレームを保持する。
     */
    fun resolve(def: SpriteDefinition, animation: String, elapsedMs: Long): SpriteFrame {
        var name = animation
        var remaining = elapsedMs.coerceAtLeast(0)
        while (true) {
            val anim = def.animations[name] ?: throw IllegalArgumentException("unknown animation: $name")
            val total = anim.steps.sumOf { it.ms }
            if (anim.loop) {
                return SpriteFrame(name, stepAt(anim, remaining % total).frame, finished = false)
            }
            if (remaining < total) {
                return SpriteFrame(name, stepAt(anim, remaining).frame, finished = false)
            }
            val next = anim.next ?: return SpriteFrame(name, anim.steps.last().frame, finished = true)
            remaining -= total
            name = next
        }
    }

    private fun stepAt(anim: AnimationDef, offsetMs: Long): Step {
        var acc = 0L
        for (step in anim.steps) {
            acc += step.ms
            if (offsetMs < acc) return step
        }
        return anim.steps.last()
    }

    /** 整数倍表示(基本設計書3.3): 利用可能なピクセル幅に収まる最大の整数倍率(最低1)。 */
    fun integerScale(availablePx: Int, sourceSize: Int): Int =
        (availablePx / sourceSize).coerceAtLeast(1)
}
