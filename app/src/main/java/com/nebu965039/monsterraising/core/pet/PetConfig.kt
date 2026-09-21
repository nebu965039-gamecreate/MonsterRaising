package com.nebu965039.monsterraising.core.pet

/**
 * 育成の調整値。基本設計書に数値の記載があるものはその値を既定とする。
 * 「目安」「例」とされているもの・未決のものは実装後のプレイテストで調整する(設計書12節)。
 */
data class PetConfig(
    /** 満腹度の自然減少(4.1: 目安 -1/時間) */
    val satietyDecayPerHour: Double = 1.0,
    /** 清潔度の自然減少(4.1: 目安 -0.7/時間) */
    val cleanlinessDecayPerHour: Double = 0.7,
    /** オフライン計算で減少に反映する経過時間の上限(4.1: 例 24時間) */
    val decayCapHours: Double = 24.0,
    /** なでる(4.1: 機嫌 +10) */
    val petMoodGain: Double = 10.0,
    /** 進化に必要な満腹度・清潔度(4.3: 例 60) */
    val evolutionMinGauge: Double = 60.0,
    /** この値を下回ると sad 系の表示になる機嫌(4.6: 例 30 未満) */
    val sadMoodThreshold: Double = 30.0,
    /** この値を下回ると sad 系の表示になる満腹度。4.6 に閾値の記載がないため既定は無効(null) */
    val sadSatietyThreshold: Double? = null,
    /** 新しい卵の初期ステータス。設計書に記載がないため暫定値 */
    val initialStats: PetStats = PetStats(satiety = 100.0, cleanliness = 100.0, mood = 50.0),
) {
    val decayCapMs: Long get() = (decayCapHours * MS_PER_HOUR).toLong()

    companion object {
        const val MS_PER_HOUR = 3_600_000.0
    }
}
