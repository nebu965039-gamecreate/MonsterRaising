package com.nebu965039.monsterraising.minigame.puzzle

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** 難易度と、クリアに必要な目標スコア(基本設計書5.2節。叩き台)。 */
enum class Difficulty(val targetScore: Int) {
    BEGINNER(300),
    INTERMEDIATE(800),
    ADVANCED(1500),
}

/**
 * 落ち物パズルの調整値。基本設計書に数値の記載があるものはその値を既定とする。
 * 記載のないもの(複数列・コンボの倍率、落下速度など)は暫定値で、プレイテストで調整する(設計書11節・12節)。
 */
data class PuzzleConfig(
    /** 盤面の列数(5.2節: 8 列) */
    val cols: Int = 8,
    /** 盤面の行数(5.2節: 14 行) */
    val rows: Int = 14,
    /** 制限時間(5.2節: 90 秒のタイムアタック) */
    val timeLimitMs: Long = 90_000L,
    /** 1 ライン消去の点数(5.3節: 100 点) */
    val scorePerLine: Int = 100,
    /** 同時に消した行数ごとの倍率(暫定)。1 行 → 1.0、2 行 → 1.5 … 配列の末尾は 5 行以上に使う */
    val multiLineMultipliers: List<Double> = listOf(1.0, 1.5, 2.0, 3.0, 4.0),
    /** 連続消去(コンボ)1 段ごとに加わる倍率(暫定)。2 連続目は 1.25 倍、3 連続目は 1.5 倍 … */
    val comboBonusPerStep: Double = 0.25,
    /** 落下の間隔の初期値(暫定) */
    val initialFallIntervalMs: Long = 1_000L,
    /** この時間ごとに落下が速くなる(5.2節: 時間経過で段階的に上昇。暫定) */
    val speedUpEveryMs: Long = 15_000L,
    /** 段階ごとに落下の間隔に掛ける係数(暫定) */
    val speedUpFactor: Double = 0.8,
    /** 落下の間隔の下限(暫定) */
    val minFallIntervalMs: Long = 150L,
) {
    /** 開始から [elapsedMs] 経過した時点の落下の間隔 */
    fun fallIntervalMs(elapsedMs: Long): Long {
        val steps = (elapsedMs / speedUpEveryMs).toInt()
        val interval = (initialFallIntervalMs * speedUpFactor.pow(steps)).toLong()
        return max(minFallIntervalMs, interval)
    }

    /**
     * ライン消去の得点(5.3節)。[lines] 行を同時に消し、それが [combo] 回目の連続消去のとき。
     * 1 行・1 回目は 100 点。
     */
    fun lineScore(lines: Int, combo: Int): Int {
        require(lines >= 1 && combo >= 1)
        val multi = multiLineMultipliers[minOf(lines, multiLineMultipliers.size) - 1]
        val comboMult = 1.0 + comboBonusPerStep * (combo - 1)
        return (scorePerLine * lines * multi * comboMult).roundToInt()
    }
}
