package com.nebu965039.monsterraising.core.pet

import com.nebu965039.monsterraising.core.minigame.MinigameGains
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 死亡の原因。 */
enum class DeathCause {
    /** 放置による死亡(4.4節)。ボーナスなしの卵になる */
    NEGLECT,

    /** 寿命による世代交代(4.5節)。先代の有効度の一部を引き継ぐ卵になる */
    OLD_AGE,
}

/** 端末内に保存する育成状態。 */
@Serializable
data class PetState(
    val stats: PetStats,
    val stage: Stage,
    /** 現在の段階に入った時刻(エポックms) */
    val stageEnteredAtMs: Long,
    /** ステータスを最後に更新した時刻(エポックms)。次回はここからの経過時間で減少を計算する */
    val lastUpdatedMs: Long,
    /** 満腹度が 0 になった時刻(エポックms)。0 でない間は null。放置による死亡(4.4節)の判定に使う */
    val satietyZeroSinceMs: Long? = null,
    /** 何代目か(4.5節)。死亡・寿命のたびに +1 する */
    val generation: Int = 1,
    /** 「長寿の秘薬」で延ばした寿命の合計(4.5節)。成熟期のみ使う */
    val lifespanExtensionMs: Long = 0L,
    /** この卵(または孵化直後)に至った先代の死因。孵化すると null に戻る。お知らせ表示用 */
    val lastDeathCause: DeathCause? = null,
) {
    /** 寿命が尽きる時刻(エポックms)。成熟期でなければ null(4.5節: 成熟期到達から 2 週間+延長分) */
    fun lifespanEndMs(): Long? =
        if (stage == Stage.MATURE) stageEnteredAtMs + stage.durationMs + lifespanExtensionMs else null

    companion object {
        /**
         * 新しい卵から始める。卵はステータスを持たないため、[PetStats.satiety] などは未使用。
         * ただし有効度(知力系・筋力系)の欄は、先代からの引き継ぎボーナスの入れ物として使い、孵化時に幼年期へ渡す(4.5節)。
         */
        fun newEgg(
            nowMs: Long,
            config: PetConfig = PetConfig(),
            generation: Int = 1,
            bonusIntellect: Double = 0.0,
            bonusStrength: Double = 0.0,
            deathCause: DeathCause? = null,
        ) = PetState(
            stats = config.initialStats.copy(intellect = bonusIntellect, strength = bonusStrength),
            stage = Stage.EGG,
            stageEnteredAtMs = nowMs,
            lastUpdatedMs = nowMs,
            generation = generation,
            lastDeathCause = deathCause,
        )
    }
}

/** 時間経過とお世話による状態遷移(オフライン計算方式。4.1節)。 */
object PetSimulator {
    /**
     * 前回更新からの経過時間ぶんの自然減少を反映し、死亡・進化を判定する。
     * 進化の判定は「開いた(お世話した)時点」の値で行い、放置中に進化の時刻が来ていても遡らない(4.3節)。
     */
    fun advance(state: PetState, nowMs: Long, config: PetConfig = PetConfig()): PetState {
        val decayed = decay(state, nowMs, config)
        return ending(decayed, nowMs, config) ?: evolve(decayed, nowMs, config)
    }

    fun feed(state: PetState, nowMs: Long, amount: Double, config: PetConfig = PetConfig()) =
        act(state, nowMs, config) { it.feed(amount).addMood(config.feedMoodGain) }

    fun clean(state: PetState, nowMs: Long, amount: Double, config: PetConfig = PetConfig()) =
        act(state, nowMs, config) { it.clean(amount) }

    fun pet(state: PetState, nowMs: Long, config: PetConfig = PetConfig()) =
        act(state, nowMs, config) { it.addMood(config.petMoodGain) }

    /** ミニゲームの結果を有効度(知力系・筋力系)と機嫌へ反映する(4.1節・5.4節)。卵は遊べないので反映されない。 */
    fun applyMinigame(state: PetState, nowMs: Long, gains: MinigameGains, config: PetConfig = PetConfig()) =
        act(state, nowMs, config) { it.addIntellect(gains.intellect).addStrength(gains.strength).addMood(gains.mood) }

    /**
     * 「長寿の秘薬」を使う(4.5節): 寿命が 1 週間延びる。成熟期のみ有効。
     * 経過時間ぶんの反映で死亡・寿命を迎えていた場合は、間に合わず新しい卵になる。
     */
    fun extendLifespan(state: PetState, nowMs: Long, config: PetConfig = PetConfig()): PetState {
        val settled = advance(state, nowMs, config)
        if (settled.stage != Stage.MATURE) return settled
        return settled.copy(lifespanExtensionMs = settled.lifespanExtensionMs + config.lifespanExtensionMs)
    }

    /**
     * 経過時間ぶんの減少を反映してから [change] を適用し、進化を再判定する(保留中の進化が成立しうるため)。
     * 減少の反映で死亡条件・寿命を満たした場合は、お世話は間に合わず新しい卵になる。
     * 卵はステータスを持たないため、お世話は反映されない。
     */
    fun act(state: PetState, nowMs: Long, config: PetConfig, change: (PetStats) -> PetStats): PetState {
        if (state.stage == Stage.EGG) return advance(state, nowMs, config)
        val decayed = decay(state, nowMs, config)
        ending(decayed, nowMs, config)?.let { return it }
        val changed = decayed.copy(stats = change(decayed.stats))
        // 満腹度が回復したら「0 の継続」は途切れる
        val next = if (changed.stats.satiety > 0.0) changed.copy(satietyZeroSinceMs = null) else changed
        return evolve(next, nowMs, config)
    }

    private fun decay(state: PetState, nowMs: Long, config: PetConfig): PetState {
        if (state.stage == Stage.EGG) return state.copy(lastUpdatedMs = nowMs)
        val elapsed = (nowMs - state.lastUpdatedMs).coerceAtLeast(0L).coerceAtMost(config.decayCapMs)
        val hours = elapsed / PetConfig.MS_PER_HOUR
        val hoursBelow = hoursBelowThreshold(state.stats, hours, config)
        val satiety = PetStats.clampGauge(state.stats.satiety - config.satietyDecayPerHour * hours)
        val stats = state.stats.copy(
            satiety = satiety,
            cleanliness = PetStats.clampGauge(state.stats.cleanliness - config.cleanlinessDecayPerHour * hours),
            mood = PetStats.clampGauge(state.stats.mood - config.moodDecayPerHour * hoursBelow),
        )
        val zeroSince = when {
            satiety > 0.0 -> null
            state.satietyZeroSinceMs != null -> state.satietyZeroSinceMs
            // 0 になった時刻を減少速度から逆算する(開いた時刻ではなく、実際に 0 になった時刻を記録する)
            state.stats.satiety > 0.0 && config.satietyDecayPerHour > 0.0 ->
                state.lastUpdatedMs + (state.stats.satiety / config.satietyDecayPerHour * PetConfig.MS_PER_HOUR).toLong()
            else -> state.lastUpdatedMs
        }
        return state.copy(stats = stats, lastUpdatedMs = nowMs, satietyZeroSinceMs = zeroSince)
    }

    /**
     * 死亡・寿命の判定。次の卵を返す(何も起きなければ null)。
     * 放置による死亡(4.4節)と寿命(4.5節)が同時に成立している場合は、先に起きたほうを採用する。
     */
    private fun ending(state: PetState, nowMs: Long, config: PetConfig): PetState? {
        if (state.stage == Stage.EGG) return null
        val neglectAt = neglectDeathTime(state, nowMs, config)
        val oldAgeAt = state.lifespanEndMs()?.takeIf { it <= nowMs }
        return when {
            oldAgeAt != null && (neglectAt == null || oldAgeAt <= neglectAt) ->
                // 寿命の場合に限り、先代の有効度(知力系・筋力系)それぞれの 5% を次の卵に引き継ぐ
                PetState.newEgg(
                    nowMs, config, state.generation + 1,
                    bonusIntellect = state.stats.intellect * config.lifespanBonusRate,
                    bonusStrength = state.stats.strength * config.lifespanBonusRate,
                    deathCause = DeathCause.OLD_AGE,
                )
            neglectAt != null ->
                // 「実は卵を1つ残していた」: 育成データはリセットされ、ボーナスなしの卵から再開する
                PetState.newEgg(nowMs, config, state.generation + 1, deathCause = DeathCause.NEGLECT)
            else -> null
        }
    }

    /**
     * 放置による死亡(4.4節)が成立していれば、その時刻(満腹度 0 の継続が 24 時間に達した時刻)を返す。
     * 条件: 満腹度 0 が 24 時間継続し、かつ清潔度が 20% 以下。
     * 経過時間の上限(24時間)は減少量にだけ効き、この継続時間は実際の時刻で数える。
     */
    private fun neglectDeathTime(state: PetState, nowMs: Long, config: PetConfig): Long? {
        val zeroSince = state.satietyZeroSinceMs ?: return null
        val at = zeroSince + config.deathSatietyZeroMs
        return if (nowMs >= at && state.stats.cleanliness <= config.deathCleanlinessMax) at else null
    }

    /**
     * 経過 [hours] のうち、満腹度・清潔度のどちらかが基準を下回っている時間(4.1: 機嫌が下がる期間)。
     * どちらも直線的に減るので、先に下回る側の時刻から経過の終わりまでが対象。
     */
    private fun hoursBelowThreshold(stats: PetStats, hours: Double, config: PetConfig): Double {
        fun timeUntilBelow(value: Double, ratePerHour: Double): Double = when {
            value < config.moodDecayGaugeThreshold -> 0.0
            ratePerHour <= 0.0 -> Double.POSITIVE_INFINITY
            else -> (value - config.moodDecayGaugeThreshold) / ratePerHour
        }
        val first = minOf(
            timeUntilBelow(stats.satiety, config.satietyDecayPerHour),
            timeUntilBelow(stats.cleanliness, config.cleanlinessDecayPerHour),
        )
        return (hours - first).coerceAtLeast(0.0)
    }

    /** 1 回の呼び出しで進む段階は 1 つまで。新しい段階の開始時刻は判定時点([nowMs])とする。 */
    private fun evolve(state: PetState, nowMs: Long, config: PetConfig): PetState =
        when (val r = Evolution.check(state.stage, state.stageEnteredAtMs, nowMs, state.stats, config)) {
            is EvolutionCheck.Ready -> if (state.stage == Stage.EGG) {
                // 孵化: 初期ステータスを付与し、先代からの引き継ぎ(有効度)を幼年期へ渡す
                state.copy(
                    stage = r.next,
                    stageEnteredAtMs = nowMs,
                    stats = config.initialStats.copy(intellect = state.stats.intellect, strength = state.stats.strength),
                    lastDeathCause = null,
                )
            } else {
                state.copy(stage = r.next, stageEnteredAtMs = nowMs)
            }
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
