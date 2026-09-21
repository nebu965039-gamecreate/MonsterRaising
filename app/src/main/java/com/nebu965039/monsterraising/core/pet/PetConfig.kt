package com.nebu965039.monsterraising.core.pet

/**
 * 育成の調整値。基本設計書に数値の記載があるものはその値を既定とする。
 * 「目安」「例」とされているもの・未決のものは実装後のプレイテストで調整する(設計書12節)。
 */
data class PetConfig(
    /** 満腹度の自然減少(4.1: 10 分ごとに -1 = -6/時間) */
    val satietyDecayPerHour: Double = 6.0,
    /** 清潔度の自然減少(4.1: 10 分ごとに -1 = -6/時間) */
    val cleanlinessDecayPerHour: Double = 6.0,
    /**
     * オフライン計算で減少に反映する経過時間の上限(4.1: 24時間)。
     * 方針: 満腹度・清潔度とも、満タンから上限より前(約 17 時間)に 0 へ届くよう減少速度を決める。
     * そうすれば上限が働く時点では下限に達しており、上限は最終的な値に影響しない。
     */
    val decayCapHours: Double = 24.0,
    /** 放置による死亡: 満腹度 0 がこの時間続く(4.4: 24 時間) */
    val deathSatietyZeroHours: Double = 24.0,
    /** 放置による死亡: そのとき清潔度がこの値以下(4.4: 20%) */
    val deathCleanlinessMax: Double = 20.0,
    /** 掃除で汚れ 1 箇所を落とすごとの清潔度の回復(10.2.2: +20。5 箇所で満了) */
    val cleanGainPerStain: Double = 20.0,
    /** なでる(4.1: 機嫌 +10) */
    val petMoodGain: Double = 10.0,
    /** 餌やりで上がる機嫌(4.1)。設計書に数値がないため暫定値 */
    val feedMoodGain: Double = 5.0,
    /** 満腹度・清潔度のどちらかがこの値を下回っている間、機嫌が下がる(4.1) */
    val moodDecayGaugeThreshold: Double = 60.0,
    /** 上の状態の間の機嫌の減少(4.1: -5/時間) */
    val moodDecayPerHour: Double = 5.0,
    /** 「長寿の秘薬」1 個で延びる寿命(4.5: 1 週間) */
    val lifespanExtensionDays: Double = 7.0,
    /** 寿命による世代交代で次の卵へ引き継ぐ有効度の割合(4.5: 5%) */
    val lifespanBonusRate: Double = 0.05,
    /** 進化に必要な満腹度・清潔度(4.3: 例 60) */
    val evolutionMinGauge: Double = 60.0,
    /** この値以下で sad 系の表示になる機嫌(4.6: 機嫌 30 以下) */
    val sadMoodThreshold: Double = 30.0,
    /** この値以下で sad 系の表示になる満腹度(4.6: 満腹度 30 以下)。null なら満腹度は見ない */
    val sadSatietyThreshold: Double? = 30.0,
    /** 幼年期に入った時点で付与する初期ステータス(卵はステータスを持たない。暫定値) */
    val initialStats: PetStats = PetStats(satiety = 100.0, cleanliness = 100.0, mood = 50.0),
) {
    val decayCapMs: Long get() = (decayCapHours * MS_PER_HOUR).toLong()

    val deathSatietyZeroMs: Long get() = (deathSatietyZeroHours * MS_PER_HOUR).toLong()

    val lifespanExtensionMs: Long get() = (lifespanExtensionDays * 24 * MS_PER_HOUR).toLong()

    companion object {
        const val MS_PER_HOUR = 3_600_000.0
    }
}
