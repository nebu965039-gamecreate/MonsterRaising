package com.nebu965039.monsterraising.core.pet

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 端末内に保存する育成状態。 */
@Serializable
data class PetState(
    val stats: PetStats,
    val stage: Stage,
    /** 現在の段階に入った時刻(エポックms) */
    val stageEnteredAtMs: Long,
    /** ステータスを最後に更新した時刻(エポックms)。次回はここからの経過時間で減少を計算する */
    val lastUpdatedMs: Long,
) {
    companion object {
        /** 新しい卵から始める */
        fun newEgg(nowMs: Long, config: PetConfig = PetConfig()) = PetState(
            stats = config.initialStats,
            stage = Stage.EGG,
            stageEnteredAtMs = nowMs,
            lastUpdatedMs = nowMs,
        )
    }
}

/** 時間経過とお世話による状態遷移(オフライン計算方式。4.1節)。 */
object PetSimulator {
    /** 前回更新からの経過時間ぶんの自然減少を反映し、進化を判定する。 */
    fun advance(state: PetState, nowMs: Long, config: PetConfig = PetConfig()): PetState =
        evolve(decay(state, nowMs, config), nowMs, config)

    fun feed(state: PetState, nowMs: Long, amount: Double, config: PetConfig = PetConfig()) =
        act(state, nowMs, config) { it.feed(amount) }

    fun clean(state: PetState, nowMs: Long, amount: Double, config: PetConfig = PetConfig()) =
        act(state, nowMs, config) { it.clean(amount) }

    fun pet(state: PetState, nowMs: Long, config: PetConfig = PetConfig()) =
        act(state, nowMs, config) { it.addMood(config.petMoodGain) }

    /** 経過時間ぶんの減少を反映してから [change] を適用し、進化を再判定する(保留中の進化が成立しうるため)。 */
    fun act(state: PetState, nowMs: Long, config: PetConfig, change: (PetStats) -> PetStats): PetState {
        val decayed = decay(state, nowMs, config)
        return evolve(decayed.copy(stats = change(decayed.stats)), nowMs, config)
    }

    private fun decay(state: PetState, nowMs: Long, config: PetConfig): PetState {
        val elapsed = (nowMs - state.lastUpdatedMs).coerceAtLeast(0L).coerceAtMost(config.decayCapMs)
        val hours = elapsed / PetConfig.MS_PER_HOUR
        val stats = state.stats.copy(
            satiety = PetStats.clampGauge(state.stats.satiety - config.satietyDecayPerHour * hours),
            cleanliness = PetStats.clampGauge(state.stats.cleanliness - config.cleanlinessDecayPerHour * hours),
        )
        return state.copy(stats = stats, lastUpdatedMs = nowMs)
    }

    /** 1 回の呼び出しで進む段階は 1 つまで。新しい段階の開始時刻は判定時点([nowMs])とする。 */
    private fun evolve(state: PetState, nowMs: Long, config: PetConfig): PetState =
        when (val r = Evolution.check(state.stage, state.stageEnteredAtMs, nowMs, state.stats, config)) {
            is EvolutionCheck.Ready -> state.copy(stage = r.next, stageEnteredAtMs = nowMs)
            else -> state
        }
}

object PetStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: PetState): String = json.encodeToString(PetState.serializer(), state)

    /** 壊れたデータ・未知の形式は null(呼び出し側で新しい卵から始める) */
    fun decode(text: String): PetState? = try {
        json.decodeFromString(PetState.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
