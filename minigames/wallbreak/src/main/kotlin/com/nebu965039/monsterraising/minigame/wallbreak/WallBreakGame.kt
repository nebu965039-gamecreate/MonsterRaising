package com.nebu965039.monsterraising.minigame.wallbreak

import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

/**
 * 壁破りゲームの調整値(基本設計書7.2節・7.4節)。記載のないもの(コンボの倍率)は暫定値。
 */
data class WallBreakConfig(
    /** 制限時間(7.2節: 60 秒のタイムアタック) */
    val timeLimitMs: Long = 60_000L,
    /** 不正解のときに減る残り時間(7.2節: 3 秒) */
    val penaltyMs: Long = 3_000L,
    /** 連続正解(コンボ)1 段ごとに 1 枚あたりの得点に加わる倍率(暫定): 2 連続目 ×1.1、3 連続目 ×1.2 … */
    val comboBonusPerStep: Double = 0.1,
    /** コンボによる倍率の上限(暫定) */
    val maxComboMultiplier: Double = 2.0,
)

/** [WallBreakGame.choose] の結果。 */
enum class ChoiceOutcome {
    /** 正解: 壁を破壊して前進 */
    CORRECT,

    /** 不正解だが「ミス無効」で時間が減らず、壁は破壊された(コンボは無効) */
    NULLIFIED,

    /** 不正解: 壁に衝突して残り時間が減った */
    MISS,

    /** 終了後・範囲外の選択などで、何も起きなかった */
    IGNORED,
}

enum class WallBreakStatus { Playing, TimeUp }

/** 1 プレイの結果。育成への反映(筋力系有効度・機嫌)は呼び出し側で行う(7.5節)。 */
data class WallBreakResult(
    val difficulty: WallDifficulty,
    val rule: RuleKind,
    /** 突破した壁の枚数(ミス無効で破壊した壁を含む) */
    val wallsBroken: Int,
    /** コンボ倍率を含む得点(7.4節) */
    val score: Int,
    val maxCombo: Int,
    val mistakes: Int,
    val nullifiedUsed: Int,
    /** 目標の突破数に到達したか(クリア判定。7.2節) */
    val cleared: Boolean,
)

/**
 * 壁破りゲームの 1 プレイ(基本設計書7節)。時間は [tick] で外から進めるので、UI・テストのどちらからも同じ結果になる。
 *
 * ルール(文字色/単語)はプレイ開始時に決まり、そのプレイ中は固定される。
 * [nullifyCount] は「ミス無効」の回数(7.3節。育てたモンスターの筋力系有効度と進化段階から求めて渡す)。
 */
class WallBreakGame(
    val difficulty: WallDifficulty,
    val nullifyCount: Int = 0,
    val config: WallBreakConfig = WallBreakConfig(),
    private val random: Random = Random.Default,
    /** null なら、開始時にランダムに決める */
    rule: RuleKind? = null,
) {
    val rule: RuleKind = rule ?: RuleKind.entries.random(random)

    var prompt: Prompt = PromptGenerator.next(difficulty, this.rule, random)
        private set
    var status: WallBreakStatus = WallBreakStatus.Playing
        private set
    var wallsBroken: Int = 0
        private set
    var combo: Int = 0
        private set
    var maxCombo: Int = 0
        private set
    var mistakes: Int = 0
        private set
    var nullifyLeft: Int = nullifyCount.coerceAtLeast(0)
        private set
    var elapsedMs: Long = 0L
        private set

    /** 目標の突破数に到達した時刻(未到達なら null)。到達後も制限時間まで続けてよい */
    var clearedAtMs: Long? = null
        private set

    private var scoreExact = 0.0
    private var penaltyTotalMs = 0L

    val score: Int get() = floor(scoreExact).toInt()

    val timeLeftMs: Long get() = (config.timeLimitMs - elapsedMs - penaltyTotalMs).coerceAtLeast(0L)

    val isFinished: Boolean get() = status != WallBreakStatus.Playing

    /** 時間を [dtMs] 進める。残り時間が 0 になったら終了 */
    fun tick(dtMs: Long) {
        if (isFinished || dtMs <= 0) return
        elapsedMs += min(dtMs, timeLeftMs)
        updateStatus()
    }

    /** 左から [index] 番目の壁を選ぶ。 */
    fun choose(index: Int): ChoiceOutcome {
        if (isFinished || index !in prompt.walls.indices) return ChoiceOutcome.IGNORED
        val outcome = when {
            index == prompt.answerIndex -> {
                combo++
                maxCombo = maxOf(maxCombo, combo)
                breakWall(comboMultiplier())
                ChoiceOutcome.CORRECT
            }
            nullifyLeft > 0 -> {
                // ミス無効: 時間は減らず、壁は破壊される。ただしコンボは無効になる(7.3節)
                nullifyLeft--
                combo = 0
                breakWall(1.0)
                ChoiceOutcome.NULLIFIED
            }
            else -> {
                mistakes++
                combo = 0
                penaltyTotalMs += config.penaltyMs
                ChoiceOutcome.MISS
            }
        }
        prompt = PromptGenerator.next(difficulty, rule, random)
        updateStatus()
        return outcome
    }

    fun result() = WallBreakResult(
        difficulty, rule, wallsBroken, score, maxCombo, mistakes,
        nullifiedUsed = nullifyCount.coerceAtLeast(0) - nullifyLeft,
        cleared = clearedAtMs != null,
    )

    private fun breakWall(points: Double) {
        wallsBroken++
        scoreExact += points
        if (clearedAtMs == null && wallsBroken >= difficulty.targetWalls) clearedAtMs = elapsedMs
    }

    /** 連続正解による倍率(7.4節)。1 回目は等倍 */
    private fun comboMultiplier(): Double =
        min(config.maxComboMultiplier, 1.0 + config.comboBonusPerStep * (combo - 1))

    private fun updateStatus() {
        if (status == WallBreakStatus.Playing && timeLeftMs <= 0) status = WallBreakStatus.TimeUp
    }
}
