package com.nebu965039.monsterraising.core.pet

import kotlinx.serialization.Serializable

/** ステータス(基本設計書4.1)。UI・Android 非依存。 */
@Serializable
data class PetStats(
    /** 満腹度 0〜100 */
    val satiety: Double,
    /** 清潔度 0〜100 */
    val cleanliness: Double,
    /** 機嫌 0〜100 */
    val mood: Double,
    /** 有効度(知力系)。累積値で上限なし・減少しない */
    val intellect: Double = 0.0,
    /** 有効度(筋力系)。累積値で上限なし・減少しない */
    val strength: Double = 0.0,
) {
    fun feed(amount: Double) = copy(satiety = clampGauge(satiety + amount))

    fun clean(amount: Double) = copy(cleanliness = clampGauge(cleanliness + amount))

    fun addMood(amount: Double) = copy(mood = clampGauge(mood + amount))

    fun addIntellect(amount: Double) = copy(intellect = intellect + amount.coerceAtLeast(0.0))

    fun addStrength(amount: Double) = copy(strength = strength + amount.coerceAtLeast(0.0))

    companion object {
        const val GAUGE_MAX = 100.0

        fun clampGauge(v: Double): Double = v.coerceIn(0.0, GAUGE_MAX)
    }
}
