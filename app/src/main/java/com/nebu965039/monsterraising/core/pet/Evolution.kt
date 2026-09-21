package com.nebu965039.monsterraising.core.pet

/** 進化判定の結果。 */
sealed interface EvolutionCheck {
    /** 進化先がない(成熟期) */
    data object Final : EvolutionCheck

    /** 段階の必要時間に未達。あと [remainingMs] */
    data class Waiting(val remainingMs: Long) : EvolutionCheck

    /** 必要時間は満たしたが、満腹度・清潔度が基準未満のため保留(ペナルティなし。4.3節) */
    data object Pending : EvolutionCheck

    /** 進化する */
    data class Ready(val next: Stage) : EvolutionCheck
}

/** 通常ルート(4.3節)。同数の場合の扱いは未決のため null を返す。 */
enum class Route { INTELLECT, STRENGTH }

object Evolution {
    /**
     * 進化判定(スナップショット方式。4.3節)。
     * 過去の推移は見ず、必要時間を満たした「その時点」の満腹度・清潔度だけを基準値と比べる。
     */
    fun check(stage: Stage, stageEnteredAtMs: Long, nowMs: Long, stats: PetStats, config: PetConfig): EvolutionCheck {
        val next = stage.next() ?: return EvolutionCheck.Final
        val remaining = stageEnteredAtMs + stage.durationMs - nowMs
        if (remaining > 0) return EvolutionCheck.Waiting(remaining)
        // 卵はステータスを持たず、時間だけで孵化する(幼年期から初期値を付与)
        val ok = stage == Stage.EGG ||
            (stats.satiety >= config.evolutionMinGauge && stats.cleanliness >= config.evolutionMinGauge)
        return if (ok) EvolutionCheck.Ready(next) else EvolutionCheck.Pending
    }

    /** 知力/筋力の優劣による通常ルート。同数は未決のため null。 */
    fun normalRoute(stats: PetStats): Route? = when {
        stats.intellect > stats.strength -> Route.INTELLECT
        stats.strength > stats.intellect -> Route.STRENGTH
        else -> null
    }
}
