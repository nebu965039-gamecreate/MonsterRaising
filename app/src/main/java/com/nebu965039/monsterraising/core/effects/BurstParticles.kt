package com.nebu965039.monsterraising.core.effects

import kotlin.math.max
import kotlin.random.Random

/** はじけ飛ぶ破片 1 つの、ある時点での状態。座標は「壁 1 枚の大きさ」を 1 とした、中心からのずれ。 */
data class Fragment(
    val dx: Float,
    val dy: Float,
    /** 一辺の長さ(壁 1 枚の大きさを 1 とする) */
    val size: Float,
    /** 回転角(度) */
    val rotation: Float,
    /** 不透明度 0〜1 */
    val alpha: Float,
)

/**
 * 壁が砕けて破片がはじけ飛ぶ演出(壁破りゲームの正解の演出)の計算。UI・Android 非依存で、乱数の種を渡せば毎回同じ動きになる。
 * 破片は中心から四方に(やや上向きに)飛び出し、重力で落ちながら、小さく・薄くなって消える。
 */
object BurstParticles {
    const val DURATION_MS = 700L
    const val DEFAULT_COUNT = 18

    private const val GRAVITY = 4.5f

    /** 開始から [elapsedMs] 経過した時点の破片。演出が終わったあと(または開始前)は空 */
    fun fragments(seed: Int, elapsedMs: Long, count: Int = DEFAULT_COUNT): List<Fragment> {
        if (elapsedMs < 0 || elapsedMs >= DURATION_MS) return emptyList()
        val t = elapsedMs / 1000f
        val p = elapsedMs.toFloat() / DURATION_MS
        val random = Random(seed)
        return List(count) {
            // 破片ごとの初速・大きさ・回転の速さ(種から決まるので、フレームをまたいでも同じ破片として動く)
            val vx = random.nextFloat() * 3.2f - 1.6f
            val vy = -(random.nextFloat() * 1.8f + 0.4f)
            val base = random.nextFloat() * 0.12f + 0.10f
            val spin = random.nextFloat() * 4f - 2f
            Fragment(
                dx = vx * t,
                dy = vy * t + 0.5f * GRAVITY * t * t,
                size = base * (1f - 0.5f * p),
                rotation = spin * t * 360f,
                alpha = max(0f, 1f - p * p),
            )
        }
    }
}
