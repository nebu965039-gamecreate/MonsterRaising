package com.nebu965039.monsterraising.core.widget

import com.nebu965039.monsterraising.core.pet.PetAppearance
import com.nebu965039.monsterraising.core.pet.PetConfig
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.Stage
import kotlin.math.roundToInt

/**
 * ウィジェットに表示する内容(UI・Android 非依存)。
 * ウィジェットは静止画の切り替えのみ(基本設計書3.1・ロードマップ Phase 3)なので、状態から 1 枚のフレームを選ぶ。
 */
data class PetWidgetModel(
    /** 表示するフレームのキー(キャラクター定義の frames のキー) */
    val frameKey: String,
    val generation: Int,
    val stage: Stage,
    /** 卵はステータスを持たず、お世話もできない */
    val isEgg: Boolean,
    /** ステータスの 10 段階ドット表示(4.7節)。卵は null */
    val satietyDots: Int?,
    val cleanlinessDots: Int?,
    val moodDots: Int?,
    /** 探索の一行表示(「探索中(残り〜)」「探索完了!」)。探索していなければ null(8.6節) */
    val explorationNote: String? = null,
    /** ごはんの所持数(餌のボタンに表示する。餌やりは 1 個消費する) */
    val riceCount: Int = 0,
) {
    /** 餌をあげられるか: 卵ではなく、ごはんがある */
    val canFeed: Boolean get() = !isEgg && riceCount > 0

    companion object {
        const val DOT_MAX = 10

        fun of(state: PetState, config: PetConfig = PetConfig(), explorationNote: String? = null, riceCount: Int = 0): PetWidgetModel {
            val isEgg = state.stage == Stage.EGG
            return PetWidgetModel(
                frameKey = if (isEgg) FRAME_NORMAL else frameFor(PetAppearance.baseAnimation(state.stats, config)),
                generation = state.generation,
                stage = state.stage,
                isEgg = isEgg,
                satietyDots = if (isEgg) null else dots(state.stats.satiety),
                cleanlinessDots = if (isEgg) null else dots(state.stats.cleanliness),
                moodDots = if (isEgg) null else dots(state.stats.mood),
                explorationNote = explorationNote,
                riceCount = riceCount,
            )
        }

        /** 内部値 0〜100 を、10 段階のドット数(0〜10)に丸める(4.7節) */
        fun dots(value: Double): Int = (value / 10.0).roundToInt().coerceIn(0, DOT_MAX)

        private const val FRAME_NORMAL = "normal"

        /** 基本アニメーション名から、静止画のフレームキーへ */
        private fun frameFor(animation: String): String = when (animation) {
            PetAppearance.SAD -> "sad"
            PetAppearance.DIRTY -> "dirty"
            else -> FRAME_NORMAL
        }
    }
}
