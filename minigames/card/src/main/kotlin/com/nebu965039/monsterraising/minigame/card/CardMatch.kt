package com.nebu965039.monsterraising.minigame.card

import kotlin.random.Random

enum class Phase {
    /** ラウンドの開始: 賭けるコインを選ぶ */
    Betting,

    /** プレイヤーの番: 引く / 止める / モンスターカード */
    PlayerTurn,

    /** ラウンドの結果を表示中 */
    RoundOver,

    /** マッチ終了 */
    MatchOver,
}

enum class RoundWinner { PLAYER, NPC, DRAW }

data class RoundOutcome(
    val round: Int,
    val bet: Int,
    val playerTotal: Int,
    val npcTotal: Int,
    val winner: RoundWinner,
    val playerBust: Boolean,
    val npcBust: Boolean,
    /** 「耐える」で超過を免れた */
    val endured: Boolean,
)

enum class MatchOutcome {
    WIN,
    LOSE,

    /** 同数(引き分け): 無効試合(6.2節) */
    DRAW,
}

data class MatchResult(
    val level: NpcLevel,
    val outcome: MatchOutcome,
    val playerCoins: Int,
    val npcCoins: Int,
    val roundsPlayed: Int,
    val monsterCard: MonsterCard?,
    val monsterCardUsed: Boolean,
)

/**
 * カード×NPC戦の 1 マッチ(基本設計書6節)。ブラックジャックを骨格にしたコイン賭けの対戦。
 *
 * 1 ラウンドの流れ: 賭け([placeBet]) → 配札(双方 2 枚、NPC の 2 枚目は伏せる) → プレイヤーが [draw] / [stand]
 * → NPC が閾値まで引く → 目標値(20)に近い側の勝ち(超えたら負け) → 勝者が賭けたコインを敗者から得る。
 * 同数のラウンドはコインの移動なし(暫定)。5 ラウンド消化、またはどちらかのコインが 0 でマッチ終了。
 *
 * 不正な呼び出し(番でないときの操作など)は何も起こさず false を返す。
 */
class CardMatch(
    val level: NpcLevel,
    val monsterCard: MonsterCard? = null,
    val config: CardConfig = CardConfig(),
    /** ラウンドごとの山札の作り方。テストでは順番を決め打ちにできる */
    private val deckFactory: () -> ArrayDeque<Card> = { Decks.shuffled(Random.Default, config) },
) {
    var playerCoins: Int = config.startCoins
        private set
    var npcCoins: Int = config.startCoins
        private set

    /** 今のラウンド(1 から) */
    var round: Int = 1
        private set
    var phase: Phase = Phase.Betting
        private set
    var bet: Int = 0
        private set

    private var deck = ArrayDeque<Card>()
    private val player = mutableListOf<Card>()
    private val npc = mutableListOf<Card>()

    val playerHand: List<Card> get() = player.toList()

    /** NPC の 2 枚目は、見せられるまで null(伏せ札)。ラウンドが終わると全部見える */
    var npcHiddenRevealed: Boolean = false
        private set
    var monsterCardUsed: Boolean = false
        private set
    private var steadyArmed = false

    var lastOutcome: RoundOutcome? = null
        private set
    private val _history = mutableListOf<RoundOutcome>()
    val history: List<RoundOutcome> get() = _history.toList()

    val playerTotal: Int get() = player.sumOf { it.value }

    /** NPC の手札(伏せ札は null) */
    fun npcCardsShown(): List<Card?> = npc.mapIndexed { i, c -> if (i == 1 && !npcHiddenRevealed) null else c }

    /** 見えている NPC のカードの合計 */
    val npcShownTotal: Int get() = npcCardsShown().sumOf { it?.value ?: 0 }

    val npcTotal: Int get() = npc.sumOf { it.value }

    /** 賭けられる最大枚数: 上限 3 枚、かつ双方が払える範囲(負けた側が払えなくならないように。暫定) */
    fun maxBet(): Int = minOf(config.maxBet, playerCoins, npcCoins)

    val isMatchOver: Boolean get() = phase == Phase.MatchOver

    /** このラウンドの後にマッチが終わるか(結果表示のボタンの文言用) */
    fun endsAfterThisRound(): Boolean = playerCoins == 0 || npcCoins == 0 || round >= config.maxRounds

    fun canUseMonsterCard(): Boolean {
        if (phase != Phase.PlayerTurn || monsterCardUsed) return false
        return when (monsterCard) {
            MonsterCard.PEEK -> !npcHiddenRevealed
            MonsterCard.REDRAW -> player.isNotEmpty() && deck.isNotEmpty()
            MonsterCard.STEADY -> deck.size >= 2
            MonsterCard.ENDURE, null -> false // ENDURE は超過したときに自動で働く
        }
    }

    /** 賭けるコインを決めて、配札する。[amount] は 1 〜 [maxBet] */
    fun placeBet(amount: Int): Boolean {
        if (phase != Phase.Betting || amount !in 1..maxBet()) return false
        bet = amount
        deck = deckFactory()
        player.clear()
        npc.clear()
        npcHiddenRevealed = false
        steadyArmed = false
        repeat(config.initialCards) {
            player += deck.removeFirst()
            npc += deck.removeFirst()
        }
        phase = Phase.PlayerTurn
        return true
    }

    /** カードを 1 枚引く(「見極め」が使用中なら 2 枚引いて 1 枚選ぶ)。山札が尽きていたら引けない */
    fun draw(): Boolean {
        if (phase != Phase.PlayerTurn || deck.isEmpty()) return false
        val card = if (steadyArmed && deck.size >= 2) {
            steadyArmed = false
            pickBetter(deck.removeFirst(), deck.removeFirst())
        } else {
            steadyArmed = false
            deck.removeFirst()
        }
        player += card
        afterPlayerCard()
        return true
    }

    /** 止める。NPC の番になり、ラウンドが決着する */
    fun stand(): Boolean {
        if (phase != Phase.PlayerTurn) return false
        resolve(playerBust = false, endured = false)
        return true
    }

    /** モンスターカードを使う(1 戦に 1 回)。使えるのは自分の番のみ。 */
    fun useMonsterCard(): Boolean {
        if (!canUseMonsterCard()) return false
        when (monsterCard) {
            MonsterCard.PEEK -> npcHiddenRevealed = true
            MonsterCard.REDRAW -> {
                player.removeAt(player.lastIndex)
                player += deck.removeFirst()
            }
            MonsterCard.STEADY -> steadyArmed = true
            else -> return false
        }
        monsterCardUsed = true
        if (monsterCard == MonsterCard.REDRAW) afterPlayerCard()
        return true
    }

    /** ラウンドの結果を見終えて、次のラウンドへ(またはマッチ終了へ)進む */
    fun nextRound(): Boolean {
        if (phase != Phase.RoundOver) return false
        if (endsAfterThisRound()) {
            phase = Phase.MatchOver
        } else {
            round++
            phase = Phase.Betting
        }
        return true
    }

    /** マッチ終了後の結果。終了前は null */
    fun result(): MatchResult? {
        if (phase != Phase.MatchOver) return null
        val outcome = when {
            playerCoins > npcCoins -> MatchOutcome.WIN
            playerCoins < npcCoins -> MatchOutcome.LOSE
            else -> MatchOutcome.DRAW
        }
        return MatchResult(level, outcome, playerCoins, npcCoins, _history.size, monsterCard, monsterCardUsed)
    }

    // --- 内部 ---

    /** 目標値に収まる中で最も近いほうを選ぶ。どちらも超えるなら小さいほう */
    private fun pickBetter(a: Card, b: Card): Card {
        val base = playerTotal
        val fits = listOf(a, b).filter { base + it.value <= config.target }
        return if (fits.isNotEmpty()) fits.maxBy { it.value } else listOf(a, b).minBy { it.value }
    }

    /** プレイヤーの手札が変わったあとの判定: 目標値を超えたら(耐えられなければ)そのラウンドは負け */
    private fun afterPlayerCard() {
        if (playerTotal <= config.target) return
        if (monsterCard == MonsterCard.ENDURE && !monsterCardUsed) {
            // 超えたカードを捨て、そこで止める(1 回だけ耐える)
            player.removeAt(player.lastIndex)
            monsterCardUsed = true
            resolve(playerBust = false, endured = true)
        } else {
            resolve(playerBust = true, endured = false)
        }
    }

    private fun resolve(playerBust: Boolean, endured: Boolean) {
        npcHiddenRevealed = true
        if (!playerBust) {
            // NPC は、手札の合計が閾値以上になるまで引く(固定ロジック。6.3節)
            while (npcTotal < level.stopAt && deck.isNotEmpty()) npc += deck.removeFirst()
        }
        val npcBust = !playerBust && npcTotal > config.target
        val winner = when {
            playerBust -> RoundWinner.NPC
            npcBust -> RoundWinner.PLAYER
            playerTotal > npcTotal -> RoundWinner.PLAYER
            playerTotal < npcTotal -> RoundWinner.NPC
            else -> RoundWinner.DRAW
        }
        when (winner) {
            RoundWinner.PLAYER -> { playerCoins += bet; npcCoins -= bet }
            RoundWinner.NPC -> { playerCoins -= bet; npcCoins += bet }
            RoundWinner.DRAW -> Unit
        }
        val outcome = RoundOutcome(round, bet, playerTotal, npcTotal, winner, playerBust, npcBust, endured)
        lastOutcome = outcome
        _history += outcome
        phase = Phase.RoundOver
    }
}
