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
    /** 同時に探索できる拠点の数: ふだんは 1 か所(8.6節) */
    val baseSlots: Int = 1,
    /** フレンドがいるときに追加で探索できる拠点の数(8.6節: 2 か所追加で合計 3 か所) */
    val friendExtraSlots: Int = 2,
    /** ログイン時に付与するポイント(8.2節: 1 日 1 回 100 ポイント) */
    val loginBonus: Int = 100,
    /** ログイン時に補充するごはんの数(1 日 1 回。暫定: 1 個。満腹度は 1 個で +25、1 日で約 144 減るので、足りない場合は増やす) */
    val loginRice: Int = 1,
    /** 排出確率(8.3節)。ノーマル(ごはん)、レア(ミニゲーム券)、かなりレア(装備)、超レア(長寿の秘薬)の順に累積して抽選する */
    val normalRate: Double = 0.915,
    val rareRate: Double = 0.05,
    val veryRareRate: Double = 0.03,
    // 残り(0.5%)が超レア
) {
    fun cost(plan: ExplorationPlan): Int = if (plan == ExplorationPlan.LONG) longCost else costPerRun * plan.runs

    /** 同時に探索できる拠点の数 */
    fun capacity(hasFriends: Boolean): Int = baseSlots + if (hasFriends) friendExtraSlots else 0

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
    /** 探索中(または終わって受け取り待ち)の探索。拠点ごとに 1 つまで */
    val actives: List<ActiveExploration> = emptyList(),
    /** ログインボーナスを最後に受け取った日(日付の通し番号) */
    val loginBonusDay: Long = -1L,
)

/** 探索の状況。 */
sealed interface ExplorationStatus {
    data object Idle : ExplorationStatus

    data class InProgress(val site: ExplorationSite, val plan: ExplorationPlan, val remainingMs: Long) : ExplorationStatus

    data class Finished(val site: ExplorationSite, val plan: ExplorationPlan, val rewards: Map<ItemType, Int>) : ExplorationStatus
}

/** 全拠点の探索の状況(ウィジェットの表示・通知などに使う)。 */
data class ExplorationOverview(
    val inProgress: List<ExplorationStatus.InProgress>,
    val finished: List<ExplorationStatus.Finished>,
) {
    val isEmpty: Boolean get() = inProgress.isEmpty() && finished.isEmpty()
}

sealed interface StartResult {
    data class Started(val progress: MiniGameProgress) : StartResult

    /** その拠点はすでに探索中(または受け取り待ち) */
    data object SiteBusy : StartResult

    /** 同時に探索できる拠点の数の上限に達している */
    data class NoFreeSlot(val capacity: Int) : StartResult

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

    /**
     * 探索を始める。ポイントを消費し、終わる時刻と報酬を決める。
     * 同時に探索できるのは、ふだんは 1 か所、フレンドがいれば 3 か所まで(8.6節)。同じ拠点は重ねて探索できない。
     */
    fun start(
        progress: MiniGameProgress,
        site: ExplorationSite,
        plan: ExplorationPlan,
        nowMs: Long,
        random: Random = Random.Default,
        config: ExplorationConfig = ExplorationConfig(),
        hasFriends: Boolean = false,
    ): StartResult {
        val actives = progress.exploration.actives
        if (actives.any { it.site == site }) return StartResult.SiteBusy
        val capacity = config.capacity(hasFriends)
        if (actives.size >= capacity) return StartResult.NoFreeSlot(capacity)
        val cost = config.cost(plan)
        val inventory = progress.inventory.spendPoints(cost)
            ?: return StartResult.NotEnoughPoints(cost, progress.inventory.explorationPoints)
        val rewards = rollRewards(site, plan, random, config).mapKeys { it.key.name }
        val active = ActiveExploration(site, plan, nowMs, nowMs + config.duration(plan), rewards)
        return StartResult.Started(
            progress.copy(inventory = inventory, exploration = progress.exploration.copy(actives = actives + active)),
        )
    }

    /**
     * その拠点で、すでに別の拠点を探索しているか(いれば「フレンドが協力しに来てくれました」と表示する。8.6節)。
     * 最初の 1 か所はひとりで、2 か所目以降はフレンドの協力による。
     */
    fun isFriendHelp(progress: MiniGameProgress): Boolean = progress.exploration.actives.isNotEmpty()

    fun status(progress: MiniGameProgress, site: ExplorationSite, nowMs: Long): ExplorationStatus {
        val a = progress.exploration.actives.firstOrNull { it.site == site } ?: return ExplorationStatus.Idle
        return statusOf(a, nowMs)
    }

    fun overview(progress: MiniGameProgress, nowMs: Long): ExplorationOverview {
        val statuses = progress.exploration.actives.map { statusOf(it, nowMs) }
        return ExplorationOverview(
            inProgress = statuses.filterIsInstance<ExplorationStatus.InProgress>().sortedBy { it.remainingMs },
            finished = statuses.filterIsInstance<ExplorationStatus.Finished>(),
        )
    }

    /** 終わった探索の報酬を持ち物へ受け取る。まだ終わっていない・探索していない場合は null。 */
    fun collect(progress: MiniGameProgress, site: ExplorationSite, nowMs: Long): CollectResult? {
        val finished = status(progress, site, nowMs) as? ExplorationStatus.Finished ?: return null
        var inventory = progress.inventory
        for ((item, n) in finished.rewards) inventory = inventory.add(item, n)
        return CollectResult(
            progress.copy(
                inventory = inventory,
                exploration = progress.exploration.copy(actives = progress.exploration.actives.filterNot { it.site == site }),
            ),
            site,
            finished.rewards,
        )
    }

    /**
     * ウィジェットに出す探索の一行表示(8.6節)。探索が終わっていれば「探索完了!」、探索中なら「探索中(残り〜)」。
     * 複数の拠点を探索しているときは、最も早く終わるものの残り時間を出す。何もなければ null。
     */
    fun summaryLine(overview: ExplorationOverview): String? = when {
        overview.finished.isNotEmpty() ->
            if (overview.finished.size == 1) "探索完了!" else "探索完了!(${overview.finished.size}か所)"
        overview.inProgress.isNotEmpty() -> {
            val first = overview.inProgress.first()
            if (overview.inProgress.size == 1) {
                "探索中(残り ${remainingText(first.remainingMs)})"
            } else {
                "探索中 ${overview.inProgress.size}か所(最短 残り ${remainingText(first.remainingMs)})"
            }
        }
        else -> null
    }

    private fun statusOf(a: ActiveExploration, nowMs: Long): ExplorationStatus =
        if (nowMs >= a.endsAtMs) {
            ExplorationStatus.Finished(a.site, a.plan, a.rewards.mapKeys { ItemType.valueOf(it.key) })
        } else {
            ExplorationStatus.InProgress(a.site, a.plan, a.endsAtMs - nowMs)
        }

    /** ログインボーナス(8.2節: 1 日 1 回、探索ポイント 100 とごはん 1 個)。受け取れたら true。 */
    fun claimLoginBonus(progress: MiniGameProgress, day: Long, config: ExplorationConfig = ExplorationConfig()): Pair<MiniGameProgress, Boolean> {
        if (progress.exploration.loginBonusDay == day) return progress to false
        return progress.copy(
            inventory = progress.inventory.addPoints(config.loginBonus).add(ItemType.RICE, config.loginRice),
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
