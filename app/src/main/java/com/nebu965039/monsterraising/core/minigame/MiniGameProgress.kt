package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.core.exploration.ExplorationState
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

/**
 * ミニゲーム共通の調整値(基本設計書2.2節・5.4節)。
 * 数値の記載がないもの(有効度の 1 日の上限、日付の切り替わり)は暫定値で、プレイテストで調整する。
 */
data class MiniGameConfig(
    /** 1 日のプレイ回数の上限(2.2節: ミニゲームごとに 3 回) */
    val basePlays: Int = 3,
    /** リワード広告による追加回数の 1 日の上限(2.2節: 叩き台 2 回) */
    val maxAdPlays: Int = 2,
    /**
     * ミニゲームで得られる有効度の 1 日の上限(5.4節「1日の獲得上限あり」)。知力系・筋力系それぞれの、全ミニゲーム合計。
     * 設計書に数値がないため暫定値。null なら上限なし。
     */
    val dailyEffectivenessCap: Double? = 60.0,
    /** 日付が切り替わる時刻(暫定: 0 時) */
    val dayStartHour: Int = 0,
)

/** 「今日」の通し番号。日付をまたいだかどうかの判定に使う。 */
object DayClock {
    fun dayIndex(nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), dayStartHour: Int = 0): Long {
        val offsetMs = zone.rules.getOffset(Instant.ofEpochMilli(nowMs)).totalSeconds * 1000L
        return Math.floorDiv(nowMs + offsetMs - dayStartHour * 3_600_000L, 86_400_000L)
    }
}

/** 1 日ぶんの記録。日付が変わると空に戻る。 */
@Serializable
data class DailyState(
    val day: Long = -1L,
    /** ゲームごとの、今日のプレイ回数 */
    val plays: Map<String, Int> = emptyMap(),
    /** ゲームごとの、今日の広告による追加回数(2.2節) */
    val adPlays: Map<String, Int> = emptyMap(),
    /** ゲームごとの、今日のミニゲーム券による追加回数(8.4節) */
    val ticketPlays: Map<String, Int> = emptyMap(),
    /** ミニゲームで今日得た有効度(上限の判定用) */
    val intellectGained: Double = 0.0,
    val strengthGained: Double = 0.0,
)

/** あるゲームの、今日のプレイ状況。 */
data class PlayStatus(
    val playsToday: Int,
    val allowedPlays: Int,
    val canWatchAd: Boolean,
    val tickets: Int,
) {
    val remaining: Int get() = (allowedPlays - playsToday).coerceAtLeast(0)
}

/** 1 プレイの精算結果。 */
data class Settlement(
    val progress: MiniGameProgress,
    /** 1 日の上限と装備の効果(8.4節)を考慮して、実際に育成へ反映する量(機嫌は上限の対象外) */
    val appliedGains: MinigameGains,
    /** 有効度が 1 日の上限で削られた */
    val capped: Boolean,
    /** クリアした場合の報酬 */
    val rewards: ClearRewards?,
)

/**
 * ミニゲーム共通の進行状況(1 日のプレイ回数・持ち物)。UI・Android 非依存。
 * 各操作は日付([day])を受け取り、日付が変わっていれば 1 日ぶんの記録をリセットしてから行う。
 */
@Serializable
data class MiniGameProgress(
    val daily: DailyState = DailyState(),
    val inventory: Inventory = Inventory(),
    /** 探索の状況(8節)。探索ポイント・アイテムと同じ保存領域で、まとめて更新する */
    val exploration: ExplorationState = ExplorationState(),
) {
    fun rolledTo(day: Long): MiniGameProgress = if (daily.day == day) this else copy(daily = DailyState(day = day))

    fun playStatus(game: MiniGame, day: Long, config: MiniGameConfig = MiniGameConfig()): PlayStatus {
        val d = rolledTo(day).daily
        val key = game.name
        return PlayStatus(
            playsToday = d.plays[key] ?: 0,
            allowedPlays = config.basePlays + (d.adPlays[key] ?: 0) + (d.ticketPlays[key] ?: 0),
            canWatchAd = (d.adPlays[key] ?: 0) < config.maxAdPlays,
            tickets = inventory.count(ItemType.MINIGAME_TICKET),
        )
    }

    /** プレイを始める(回数を 1 つ使う)。上限に達していれば null。途中でやめても回数は戻らない。 */
    fun startPlay(game: MiniGame, day: Long, config: MiniGameConfig = MiniGameConfig()): MiniGameProgress? {
        val rolled = rolledTo(day)
        if (rolled.playStatus(game, day, config).remaining <= 0) return null
        val key = game.name
        val d = rolled.daily
        return rolled.copy(daily = d.copy(plays = d.plays + (key to (d.plays[key] ?: 0) + 1)))
    }

    /**
     * リワード広告を見終えたときに呼ぶ: そのゲームのプレイ回数を +1 する(2.2節)。
     * 広告による追加の 1 日の上限に達していれば null。
     */
    fun addAdPlay(game: MiniGame, day: Long, config: MiniGameConfig = MiniGameConfig()): MiniGameProgress? {
        val rolled = rolledTo(day)
        if (!rolled.playStatus(game, day, config).canWatchAd) return null
        val key = game.name
        val d = rolled.daily
        return rolled.copy(daily = d.copy(adPlays = d.adPlays + (key to (d.adPlays[key] ?: 0) + 1)))
    }

    /** ミニゲーム券を 1 枚使い、そのゲームのプレイ回数を +1 する(8.4節)。券がなければ null。 */
    fun addTicketPlay(game: MiniGame, day: Long): MiniGameProgress? {
        val rolled = rolledTo(day)
        val inventory = rolled.inventory.consume(ItemType.MINIGAME_TICKET) ?: return null
        val key = game.name
        val d = rolled.daily
        return rolled.copy(
            inventory = inventory,
            daily = d.copy(ticketPlays = d.ticketPlays + (key to (d.ticketPlays[key] ?: 0) + 1)),
        )
    }

    /**
     * 1 プレイを精算する: 有効度の 1 日の上限を適用し(5.4節)、クリアなら報酬を持ち物へ加える(8.5節)。
     * [firstClear] は、その難易度を初めてクリアしたか(上級の初回クリアの装備の判定用)。
     */
    fun settle(
        game: MiniGame,
        day: Long,
        tier: RewardTier,
        cleared: Boolean,
        firstClear: Boolean,
        gains: MinigameGains,
        config: MiniGameConfig = MiniGameConfig(),
    ): Settlement {
        val rolled = rolledTo(day)
        val cap = config.dailyEffectivenessCap
        fun room(gained: Double) = if (cap == null) Double.MAX_VALUE else (cap - gained).coerceAtLeast(0.0)
        val intellect = minOf(gains.intellect, room(rolled.daily.intellectGained))
        val strength = minOf(gains.strength, room(rolled.daily.strengthGained))
        val capped = intellect < gains.intellect || strength < gains.strength

        // 装備の効果: 1 日の上限を適用したあとの増加量に上乗せする(上限の消費は上乗せ前の量で数える)
        val effect = rolled.inventory.equipEffect()
        val boosted = MinigameGains(
            intellect = intellect * (1.0 + effect.intellectGain),
            strength = strength * (1.0 + effect.strengthGain),
            mood = gains.mood,
        )

        val rewards = if (cleared) ClearRewardTable.rewards(game, tier, firstClear) else null
        var inventory = rolled.inventory
        if (rewards != null) {
            inventory = inventory.addPoints(rewards.explorationPoints)
            for ((item, n) in rewards.items) inventory = inventory.add(item, n)
        }
        val progress = rolled.copy(
            daily = rolled.daily.copy(
                intellectGained = rolled.daily.intellectGained + intellect,
                strengthGained = rolled.daily.strengthGained + strength,
            ),
            inventory = inventory,
        )
        return Settlement(progress, boosted, capped, rewards)
    }
}

object MiniGameProgressCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(progress: MiniGameProgress): String = json.encodeToString(MiniGameProgress.serializer(), progress)

    /** 壊れたデータは null(呼び出し側で初期状態から始める) */
    fun decode(text: String): MiniGameProgress? = try {
        json.decodeFromString(MiniGameProgress.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
