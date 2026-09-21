package com.nebu965039.monsterraising.core.pet

/** ステータスと見た目の連動(基本設計書4.6節)。返すのは差分画像プレイヤーのアニメーション名。 */
object PetAppearance {
    const val IDLE = "idle"
    const val SAD = "sad"
    const val DIRTY = "dirty"

    /**
     * 清潔度 0 は専用フレーム(煤+アホ毛)、機嫌(または設定時は満腹度)が閾値未満なら sad、それ以外は idle。
     * 清潔度 0 と sad が重なった場合の優先順位は設計書に記載がないため、暫定で清潔度 0 を優先する。
     */
    fun baseAnimation(stats: PetStats, config: PetConfig = PetConfig()): String = when {
        stats.cleanliness <= 0.0 -> DIRTY
        stats.mood < config.sadMoodThreshold -> SAD
        config.sadSatietyThreshold?.let { stats.satiety < it } == true -> SAD
        else -> IDLE
    }
}
