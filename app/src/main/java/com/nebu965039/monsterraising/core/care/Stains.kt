package com.nebu965039.monsterraising.core.care

import kotlin.math.hypot
import kotlin.math.min

/** 掃除の汚れのルール(基本設計書10.2.2節)。 */
object StainRules {
    /** 1 箇所の汚れが消えるまでになでる回数(10.2.2節: 5 回) */
    const val STROKES_PER_STAIN = 5

    /** 表示される汚れの最大数(清潔度 0 のとき) */
    const val MAX_STAINS = 5

    /**
     * 清潔度 [cleanliness] から満タンまでに必要な汚れの数(1 箇所 +20 なので、`ceil((100 - 清潔度) / 20)`)。
     * 残っている汚れの数の上限で、これを超える汚れは(ほかの方法で清潔度が上がったときに)消える。
     * 清潔度 0 の 5 箇所から 1 箇所ずつ落とすと、4 → 3 → … → 0 と減り、5 箇所ですべて 100 になる(10.2.2節)。
     */
    fun maxRemaining(cleanliness: Double): Int =
        kotlin.math.ceil((100.0 - cleanliness.coerceIn(0.0, 100.0)) / CLEAN_GAIN_PER_STAIN).toInt().coerceIn(0, MAX_STAINS)

    /** 汚れ 1 箇所を消したときの清潔度の回復量(10.2.2節: +20)。設定値 [com.nebu965039.monsterraising.core.pet.PetConfig.cleanGainPerStain] と揃える */
    const val CLEAN_GAIN_PER_STAIN = 20.0

    /**
     * 清潔度に応じて表示される汚れの数(10.2.2節)。
     * 80 未満で 1 箇所、60 未満で 2 箇所、40 未満で 3 箇所、20 未満で 4 箇所、0 で 5 箇所。80 以上は汚れなし。
     */
    fun countFor(cleanliness: Double): Int = when {
        cleanliness <= 0.0 -> 5
        cleanliness < 20.0 -> 4
        cleanliness < 40.0 -> 3
        cleanliness < 60.0 -> 2
        cleanliness < 80.0 -> 1
        else -> 0
    }
}

/** 背景の上にある汚れ 1 箇所。[slot] は候補地点の番号(位置は候補地点で決まる)。[strokes] はなでられた回数。 */
data class Stain(val slot: Int, val x: Float, val y: Float, val strokes: Int = 0)

/**
 * 掃除の汚れの一式(10.2.2節)。UI・Android 非依存。
 * 汚れは背景の上の候補地点(0〜1 の相対座標)に表示される。掃除モード中に、スワイプが汚れの近くを通過するたびに、
 * その汚れのなでた回数が +1 され、5 回で消える。1 回の通過は「汚れの半径の外 → 内に入った」ときに数える
 * (同じ汚れの上を指が動き続けても、1 回として数える)。
 */
class StainField(
    private val candidates: List<Pair<Float, Float>> = DEFAULT_CANDIDATES,
    /** 汚れの反応する半径(ステージの短いほうの辺に対する割合) */
    private val radiusFraction: Float = 0.09f,
) {
    private val _stains = mutableListOf<Stain>()
    private val inside = mutableSetOf<Int>()

    val stains: List<Stain> get() = _stains.toList()

    val isClean: Boolean get() = _stains.isEmpty()

    /**
     * 汚れの数を [targetCount] に合わせる。足りなければ、空いている候補地点に新しい汚れを足す(清潔度が下がった)。
     * 多ければ、番号の大きい汚れから取り除く。なでた回数は残った汚れに持ち越される(10.2.2節)。
     */
    fun sync(targetCount: Int) {
        val target = targetCount.coerceIn(0, candidates.size)
        while (_stains.size > target) {
            val removed = _stains.maxBy { it.slot }
            _stains.remove(removed)
            inside.remove(removed.slot)
        }
        while (_stains.size < target) {
            val slot = candidates.indices.first { s -> _stains.none { it.slot == s } }
            _stains += Stain(slot, candidates[slot].first, candidates[slot].second)
        }
        _stains.sortBy { it.slot }
    }

    /**
     * 清潔度に合わせて汚れを増減する。新しい汚れは、清潔度が表([StainRules.countFor])の数を下回ったときに現れる。
     * 既にある汚れは、清潔度が上がっても勝手には減らない(掃除で消したときだけ減る)。
     * ただし、掃除以外で清潔度が上がって、満タンまでに必要な数([StainRules.maxRemaining])を超えたぶんは消える。
     */
    fun syncToCleanliness(cleanliness: Double) {
        val cap = StainRules.maxRemaining(cleanliness)
        val wanted = minOf(StainRules.countFor(cleanliness), cap)
        while (_stains.size < wanted) {
            val slot = candidates.indices.first { s -> _stains.none { it.slot == s } }
            _stains += Stain(slot, candidates[slot].first, candidates[slot].second)
        }
        while (_stains.size > cap) {
            val removed = _stains.maxBy { it.slot }
            _stains.remove(removed)
            inside.remove(removed.slot)
        }
        _stains.sortBy { it.slot }
    }

    /** なでた回数をすべて 0 に戻す(掃除モードを終えたとき。なでた回数は持ち越さない) */
    fun resetStrokes() {
        for (i in _stains.indices) _stains[i] = _stains[i].copy(strokes = 0)
        inside.clear()
    }

    /** 指(スワイプ)が [x], [y](ステージ内の座標。大きさは [width]×[height])にあるときに呼ぶ。消えた汚れの番号を返す。 */
    fun touch(x: Float, y: Float, width: Float, height: Float): List<Int> {
        val radius = radiusFraction * min(width, height)
        val cleaned = mutableListOf<Int>()
        for (stain in _stains.toList()) {
            val near = hypot(x - stain.x * width, y - stain.y * height) <= radius
            if (!near) {
                inside.remove(stain.slot)
                continue
            }
            if (!inside.add(stain.slot)) continue // 同じ通過の続き
            val strokes = stain.strokes + 1
            if (strokes >= StainRules.STROKES_PER_STAIN) {
                _stains.remove(stain)
                inside.remove(stain.slot)
                cleaned += stain.slot
            } else {
                _stains[_stains.indexOf(stain)] = stain.copy(strokes = strokes)
            }
        }
        return cleaned
    }

    /** スワイプが終わった(指を離した)。次のスワイプでは、汚れの上でも新しい通過として数える */
    fun endStroke() = inside.clear()

    /** [x], [y] のそばにある汚れがあるか(汚れ以外の場所をタップして掃除モードを終える判定に使う) */
    fun hasStainNear(x: Float, y: Float, width: Float, height: Float): Boolean {
        val radius = radiusFraction * min(width, height)
        return _stains.any { hypot(x - it.x * width, y - it.y * height) <= radius }
    }

    companion object {
        /** 汚れの候補地点(ステージの相対座標)。キャラクターの周りに散らす */
        val DEFAULT_CANDIDATES: List<Pair<Float, Float>> = listOf(
            0.18f to 0.72f,
            0.82f to 0.66f,
            0.30f to 0.88f,
            0.72f to 0.86f,
            0.50f to 0.94f,
        )
    }
}

/** 掃除の状態をアプリの起動中は保持する(画面を切り替えても、残った汚れは残る。なでた回数は掃除モードを終えるとリセットされる)。 */
object CareSession {
    val stainField = StainField()
}
