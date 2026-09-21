package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.minigame.wallbreak.WallBreakResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 壁破りゲームの育成への反映の調整値(基本設計書7.5節)。数値の記載がないため暫定値。 */
data class WallBreakRewardConfig(
    /** 筋力系有効度 1 点に換算するスコア(暫定: 2 点で 1)。様子を見て調整する */
    val scorePerEffectivenessPoint: Int = 2,
    /** プレイ後の機嫌の増加(勝敗・成績に関わらず一定。他のミニゲームに揃える) */
    val moodGain: Double = 10.0,
)

object WallBreakRewards {
    /**
     * 壁破りゲームは筋力系専門(4.1節): スコアに応じて筋力系有効度を加算する(7.5節)。知力系は加算しない。
     * 1 日の獲得上限は精算時([MiniGameProgress.settle])に適用する。
     */
    fun gains(result: WallBreakResult, config: WallBreakRewardConfig = WallBreakRewardConfig()): MinigameGains =
        MinigameGains(
            intellect = 0.0,
            strength = (result.score / config.scorePerEffectivenessPoint).toDouble(),
            mood = config.moodGain,
        )

    fun tier(result: WallBreakResult): RewardTier = RewardTier.valueOf(result.difficulty.name)
}

/** 「ミス無効」の回数(基本設計書7.3節)。 */
object MissNullify {
    /** 進化段階ごとの上限: 成長期Ⅰ 1 回、成長期Ⅱ 2 回、成熟期 3 回。卵・幼年期は表にないので 0 回 */
    fun stageCap(stage: Stage): Int = when (stage) {
        Stage.GROWTH_1 -> 1
        Stage.GROWTH_2 -> 2
        Stage.MATURE -> 3
        Stage.EGG, Stage.INFANT -> 0
    }

    /** `付与回数 = min(その段階の上限, floor(筋力値 / 100))`(7.3節)。筋力値は筋力系有効度。 */
    fun count(stage: Stage, strength: Double): Int =
        minOf(stageCap(stage), (strength / 100.0).toInt().coerceAtLeast(0))

    fun forPet(state: PetState): Int = count(state.stage, state.stats.strength)
}

/** 記録の更新結果。 */
data class ScoreRecordUpdate(
    val records: ScoreRecords,
    val isNewHighScore: Boolean,
    /** その難易度を初めてクリアした(上級の初回クリアの報酬判定用。8.5節) */
    val isFirstClear: Boolean,
)

/** 難易度ごとのハイスコアとクリア済みの記録(ローカル。7.6節)。難易度は列挙名をキーにして保存する。 */
@Serializable
data class ScoreRecords(
    val highScores: Map<String, Int> = emptyMap(),
    val clearedDifficulties: Set<String> = emptySet(),
) {
    fun highScore(key: String): Int = highScores[key] ?: 0

    fun hasCleared(key: String): Boolean = key in clearedDifficulties

    fun record(key: String, score: Int, cleared: Boolean): ScoreRecordUpdate {
        val newHigh = score > highScore(key)
        val firstClear = cleared && !hasCleared(key)
        val next = copy(
            highScores = if (newHigh) highScores + (key to score) else highScores,
            clearedDifficulties = if (cleared) clearedDifficulties + key else clearedDifficulties,
        )
        return ScoreRecordUpdate(next, newHigh, firstClear)
    }
}

object ScoreRecordsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(records: ScoreRecords): String = json.encodeToString(ScoreRecords.serializer(), records)

    fun decode(text: String): ScoreRecords? = try {
        json.decodeFromString(ScoreRecords.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
