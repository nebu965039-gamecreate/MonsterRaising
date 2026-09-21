package com.nebu965039.monsterraising.core.exploration

import com.nebu965039.monsterraising.core.minigame.ItemType
import com.nebu965039.monsterraising.core.minigame.MiniGameProgress
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** 探索拠点(基本設計書8.1節): 初期リリースは洞窟・海岸・山の 3 か所。 */
enum class ExplorationSite(
    val displayName: String,
    /** 拠点画面に表示する導入の文章 */
    val intro: String,
    /** 「かなりレア」で出る装備アイテム(8.4節「入手源との対応関係」) */
    val equipment: ItemType,
) {
    CAVE("洞窟", "暗くて巨大な洞窟があります。", ItemType.EQUIP_BALANCE_CARE),
    COAST("海岸", "潮の香りのする、広い海岸があります。", ItemType.EQUIP_CLEANLINESS),
    MOUNTAIN("山", "深い緑に覆われた、険しい山があります。", ItemType.EQUIP_SATIETY),
}

/** 探索の種類(8.2節): 1 回(少し探索)と、10 連(じっくり探索)。 */
enum class ExplorationPlan(val label: String, val runs: Int) {
    SHORT("少し探索", 1),
    LONG("じっくり探索", 10),
}

/**
 * 探索の調整値(基本設計書8.2節・8.3節)。
 * 10 連は、消費が 10 回分(1000 ポイント)で、所要時間はまとめて行うボーナスにより 5 時間から 4 時間に短縮される。
 */
data class ExplorationConfig(
    /** 1 回の消費ポイントと所要時間(8.2節: 100 ポイント・30 分) */
    val costPerRun: Int = 100,
    val durationPerRunMs: Long = 30 * 60_000L,
    /** 10 連(じっくり探索)の消費ポイントと所要時間(8.2節: 1000 ポイント・4 時間) */
    val longCost: Int = 1_000,
    val longDurationMs: Long = 4 * 3_600_000L,
    /** 探索 1 回で入手するアイテムの数(8.3節: 3 個。それぞれ独立に抽選) */
    val itemsPerRun: Int = 3,
    /** ログイン時に付与するポイント(8.2節: 1 日 1 回 100 ポイント) */
    val loginBonus: Int = 100,
    /** 排出確率(8.3節)。ノーマル(ごはん)、レア(ミニゲーム券)、かなりレア(装備)、超レア(長寿の秘薬)の順に累積して抽選する */
    val normalRate: Double = 0.915,
    val rareRate: Double = 0.05,
    val veryRareRate: Double = 0.03,
    // 残り(0.5%)が超レア
) {
    fun cost(plan: ExplorationPlan): Int = if (plan == ExplorationPlan.LONG) longCost else costPerRun * plan.runs

    fun duration(plan: ExplorationPlan): Long = if (plan == ExplorationPlan.LONG) longDurationMs else durationPerRunMs * plan.runs
}

/** 探索中の内容。開始時に報酬を抽選して保存しておく(終わるまで中身は見せない)。 */
@Serializable
data class ActiveExploration(
    val site: ExplorationSite,
    val plan: ExplorationPlan,
    val startedAtMs: Long,
    val endsAtMs: Long,
    /** 抽選済みの報酬(アイテム名 → 個数) */
    val rewards: Map<String, Int>,
)

@Serializable
data class ExplorationState(
    val active: ActiveExploration? = null,
    /** ログインボーナスを最後に受け取った日(日付の通し番号) */
    val loginBonusDay: Long = -1L,
)

/** 探索の状況。 */
sealed interface ExplorationStatus {
    data object Idle : ExplorationStatus

    data class InProgress(val site: ExplorationSite, val plan: ExplorationPlan, val remainingMs: Long) : ExplorationStatus

    data class Finished(val site: ExplorationSite, val plan: ExplorationPlan, val rewards: Map<ItemType, Int>) : ExplorationStatus
}

sealed interface StartResult {
    data class Started(val progress: MiniGameProgress) : StartResult

    /** すでに探索中(同時に探索できるのは 1 か所。暫定) */
    data object AlreadyExploring : StartResult

    data class NotEnoughPoints(val needed: Int, val have: Int) : StartResult
}

data class CollectResult(val progress: MiniGameProgress, val site: ExplorationSite, val rewards: Map<ItemType, Int>)

object Exploration {
    /** 探索 1 回ぶんのアイテム 1 個を抽選する(8.3節)。 */
    fun rollItem(site: ExplorationSite, random: Random, config: ExplorationConfig = ExplorationConfig()): ItemType {
        val r = random.nextDouble()
        return when {
            r < config.normalRate -> ItemType.RICE
            r < config.normalRate + config.rareRate -> ItemType.MINIGAME_TICKET
            r < config.normalRate + config.rareRate + config.veryRareRate -> site.equipment
            else -> ItemType.ELIXIR
        }
    }

    /** [plan] の報酬(アイテム数 = 3 個 × 回数)を抽選する。 */
    fun rollRewards(
        site: ExplorationSite,
        plan: ExplorationPlan,
        random: Random,
        config: ExplorationConfig = ExplorationConfig(),
    ): Map<ItemType, Int> {
        val result = mutableMapOf<ItemType, Int>()
        repeat(plan.runs * config.itemsPerRun) {
            val item = rollItem(site, random, config)
            result[item] = (result[item] ?: 0) + 1
        }
        return result
    }

    /** 探索を始める。ポイントを消費し、終わる時刻と報酬を決める。すでに探索中、またはポイント不足なら始めない。 */
    fun start(
        progress: MiniGameProgress,
        site: ExplorationSite,
        plan: ExplorationPlan,
        nowMs: Long,
        random: Random = Random.Default,
        config: ExplorationConfig = ExplorationConfig(),
    ): StartResult {
        if (progress.exploration.active != null) return StartResult.AlreadyExploring
        val cost = config.cost(plan)
        val inventory = progress.inventory.spendPoints(cost)
            ?: return StartResult.NotEnoughPoints(cost, progress.inventory.explorationPoints)
        val rewards = rollRewards(site, plan, random, config).mapKeys { it.key.name }
        val active = ActiveExploration(site, plan, nowMs, nowMs + config.duration(plan), rewards)
        return StartResult.Started(
            progress.copy(inventory = inventory, exploration = progress.exploration.copy(active = active)),
        )
    }

    fun status(progress: MiniGameProgress, nowMs: Long): ExplorationStatus {
        val a = progress.exploration.active ?: return ExplorationStatus.Idle
        return if (nowMs >= a.endsAtMs) {
            ExplorationStatus.Finished(a.site, a.plan, a.rewards.mapKeys { ItemType.valueOf(it.key) })
        } else {
            ExplorationStatus.InProgress(a.site, a.plan, a.endsAtMs - nowMs)
        }
    }

    /** 終わった探索の報酬を持ち物へ受け取る。まだ終わっていない・探索していない場合は null。 */
    fun collect(progress: MiniGameProgress, nowMs: Long): CollectResult? {
        val finished = status(progress, nowMs) as? ExplorationStatus.Finished ?: return null
        var inventory = progress.inventory
        for ((item, n) in finished.rewards) inventory = inventory.add(item, n)
        return CollectResult(
            progress.copy(inventory = inventory, exploration = progress.exploration.copy(active = null)),
            finished.site,
            finished.rewards,
        )
    }

    /** ログインボーナス(8.2節: 1 日 1 回 100 ポイント)。受け取れたら true。 */
    fun claimLoginBonus(progress: MiniGameProgress, day: Long, config: ExplorationConfig = ExplorationConfig()): Pair<MiniGameProgress, Boolean> {
        if (progress.exploration.loginBonusDay == day) return progress to false
        return progress.copy(
            inventory = progress.inventory.addPoints(config.loginBonus),
            exploration = progress.exploration.copy(loginBonusDay = day),
        ) to true
    }

    /** 残り時間の表示用(例: 「3時間30分」「45分」)。1 分未満は「1分」に切り上げる。 */
    fun remainingText(remainingMs: Long): String {
        val minutes = ((remainingMs.coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}分"
            m == 0 -> "${h}時間"
            else -> "${h}時間${m}分"
        }
    }
}
