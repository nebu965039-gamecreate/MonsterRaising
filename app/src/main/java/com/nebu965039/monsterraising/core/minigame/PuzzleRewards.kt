package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.minigame.puzzle.PuzzleResult

/** ミニゲームの結果を育成へ反映する量(基本設計書4.1節・5.4節)。 */
data class MinigameGains(
    val intellect: Double,
    val strength: Double,
    val mood: Double,
) {
    companion object {
        val NONE = MinigameGains(0.0, 0.0, 0.0)
    }
}

/** 落ち物パズルの育成への反映の調整値。 */
data class PuzzleRewardConfig(
    /** 有効度に換算するスコアの単位(5.4節: floor(スコア/100) をそれぞれに加算) */
    val scorePerEffectivenessPoint: Int = 100,
    /** プレイ後の機嫌の増加(5.4節: スコアに関わらず一定値)。設計書に数値がないため暫定値 */
    val moodGain: Double = 10.0,
)

object PuzzleRewards {
    /**
     * 落ち物パズルはバランス型(4.1節): スコアに応じて知力系・筋力系の有効度を均等に加算する(5.4節)。
     * 機嫌は、遊んだこと自体を評価して一定値(スコアが 0 でも付与)。
     * 1 日の獲得上限(5.4節)は、日付をまたぐ記録が要るため未実装(docs/todo.md)。
     */
    fun gains(result: PuzzleResult, config: PuzzleRewardConfig = PuzzleRewardConfig()): MinigameGains {
        val points = (result.score / config.scorePerEffectivenessPoint).toDouble()
        return MinigameGains(intellect = points, strength = points, mood = config.moodGain)
    }
}
