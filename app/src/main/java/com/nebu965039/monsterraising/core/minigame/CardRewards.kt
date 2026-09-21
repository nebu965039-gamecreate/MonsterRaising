package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.core.pet.Evolution
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.Route
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.minigame.card.MatchOutcome
import com.nebu965039.monsterraising.minigame.card.MatchResult
import com.nebu965039.monsterraising.minigame.card.MonsterCard
import com.nebu965039.monsterraising.minigame.card.NpcLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** カード×NPC戦の育成への反映の調整値(基本設計書6.5節)。数値の記載がないため、すべて暫定値。 */
data class CardRewardConfig(
    /** マッチ勝利で加算する知力系有効度(NPC の強さごと) */
    val winIntellect: Map<NpcLevel, Double> = mapOf(
        NpcLevel.BEGINNER to 8.0,
        NpcLevel.INTERMEDIATE to 12.0,
        NpcLevel.ADVANCED to 16.0,
    ),
    /** 無効試合(コイン同数)で加算する知力系有効度(6.2節: 通常勝利より大幅に少ない量) */
    val drawIntellect: Double = 1.0,
    /** プレイ後の機嫌の増加(遊んだこと自体を評価して、勝敗に関わらず一定。落ち物パズルに揃える) */
    val moodGain: Double = 10.0,
)

object CardRewards {
    /**
     * カード×NPC戦は知力系専門(4.1節): 勝利で知力系有効度を加算、敗北は有効度への影響なし(6.5節)、
     * 無効試合はごく少量を加算する。筋力系は加算しない。1 日の獲得上限は精算時([MiniGameProgress.settle])に適用する。
     */
    fun gains(result: MatchResult, config: CardRewardConfig = CardRewardConfig()): MinigameGains {
        val intellect = when (result.outcome) {
            MatchOutcome.WIN -> config.winIntellect.getValue(result.level)
            MatchOutcome.DRAW -> config.drawIntellect
            MatchOutcome.LOSE -> 0.0
        }
        return MinigameGains(intellect = intellect, strength = 0.0, mood = config.moodGain)
    }

    /** クリア判定: 該当難易度の NPC に勝利すること(6.3節) */
    fun isClear(result: MatchResult): Boolean = result.outcome == MatchOutcome.WIN

    fun tier(level: NpcLevel): RewardTier = RewardTier.valueOf(level.name)
}

/** 育てたモンスターの進化段階に応じたモンスターカード(基本設計書6.4節)。 */
object MonsterCards {
    /**
     * 卵: なし / 幼年期: 先読み / 成長期(Ⅰ・Ⅱ): 引き直し /
     * 成熟期: 知力ルートは見極め、筋力ルートは耐える。
     * ルートは進化のときの状態を記録していないため、現在の有効度の優劣(4.3節の通常ルート)で判定する。同数は知力ルート扱い(暫定)。
     */
    fun forPet(state: PetState): MonsterCard? = when (state.stage) {
        Stage.EGG -> null
        Stage.INFANT -> MonsterCard.PEEK
        Stage.GROWTH_1, Stage.GROWTH_2 -> MonsterCard.REDRAW
        Stage.MATURE -> if (Evolution.normalRoute(state.stats) == Route.STRENGTH) MonsterCard.ENDURE else MonsterCard.STEADY
    }
}

/** 記録の更新結果。 */
data class CardRecordUpdate(
    val records: CardRecords,
    /** その難易度を初めてクリア(勝利)した */
    val isFirstClear: Boolean,
    /** このマッチで解放された難易度(勝ち上がり式。6.3節) */
    val newlyUnlocked: NpcLevel?,
)

/**
 * カード×NPC戦のローカル記録(6.6節: 連勝数)。難易度は列挙名で保存する。
 * NPC は勝ち上がり式で、初級に勝つと中級、中級に勝つと上級が解放される(6.3節)。
 */
@Serializable
data class CardRecords(
    /** 現在の連勝数(マッチ勝利で +1、敗北で 0、無効試合は変えない。6.5節) */
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val clearedLevels: Set<String> = emptySet(),
) {
    fun hasCleared(level: NpcLevel): Boolean = level.name in clearedLevels

    fun isUnlocked(level: NpcLevel): Boolean = when (level) {
        NpcLevel.BEGINNER -> true
        NpcLevel.INTERMEDIATE -> hasCleared(NpcLevel.BEGINNER)
        NpcLevel.ADVANCED -> hasCleared(NpcLevel.INTERMEDIATE)
    }

    fun record(result: MatchResult): CardRecordUpdate {
        val before = this
        val next = when (result.outcome) {
            MatchOutcome.WIN -> copy(
                streak = streak + 1,
                bestStreak = maxOf(bestStreak, streak + 1),
                clearedLevels = clearedLevels + result.level.name,
            )
            MatchOutcome.LOSE -> copy(streak = 0)
            MatchOutcome.DRAW -> this // 無効試合は連勝数・勝敗に影響しない(6.2節)
        }
        val firstClear = result.outcome == MatchOutcome.WIN && !before.hasCleared(result.level)
        val unlocked = NpcLevel.entries.firstOrNull { !before.isUnlocked(it) && next.isUnlocked(it) }
        return CardRecordUpdate(next, firstClear, unlocked)
    }
}

object CardRecordsCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(records: CardRecords): String = json.encodeToString(CardRecords.serializer(), records)

    fun decode(text: String): CardRecords? = try {
        json.decodeFromString(CardRecords.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
