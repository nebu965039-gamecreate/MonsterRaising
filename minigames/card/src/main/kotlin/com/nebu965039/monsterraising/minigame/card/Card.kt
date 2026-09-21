package com.nebu965039.monsterraising.minigame.card

import kotlin.random.Random

/** カードの属性。スートの代わりに、天候システム(4.8節)と同じ 5 属性を使う(6.2.1節)。ゲーム上の効果はなく、意匠のみ。 */
enum class Attribute { SUNNY, CLOUDY, RAIN, SNOW, STORM }

data class Card(val attribute: Attribute, val value: Int)

/** NPC の強さ(6.3節)。手札の合計が [stopAt] 以上になったら引くのを止める。 */
enum class NpcLevel(val stopAt: Int) {
    BEGINNER(15),
    INTERMEDIATE(17),
    ADVANCED(19),
}

/** モンスターカード(6.4節): 育てたモンスターの進化段階に応じた特殊効果カード。1 戦に 1 枚まで、1 回だけ使える。 */
enum class MonsterCard {
    /** 幼年期: 相手の伏せられた手札を 1 枚見られる(手札を 1 枚多く見られる) */
    PEEK,

    /** 成長期: 引いたカードを 1 回だけ引き直せる */
    REDRAW,

    /** 成熟期・知力ルート: 次に引くとき 2 枚引いて、目標値に近いほうを選べる(合計値のブレを軽減) */
    STEADY,

    /** 成熟期・筋力ルート: 目標値を超えたとき 1 回だけ耐える(超えたカードを捨てて、そこで止める) */
    ENDURE,
}

/**
 * カード×NPC戦の調整値(基本設計書6.2節)。記載のないものは暫定値。
 */
data class CardConfig(
    /** 目標値(6.2節: 例 20)。超えると即敗北 */
    val target: Int = 20,
    /** 開始時の双方のコイン(6.2節: 5 枚) */
    val startCoins: Int = 5,
    /** マッチのラウンド数(6.2節: 5 ラウンド) */
    val maxRounds: Int = 5,
    /** 1 ラウンドに賭けられるコインの上限(6.2節: 1〜3 枚) */
    val maxBet: Int = 3,
    /** ラウンド開始時に配る枚数(暫定: 2 枚。うち NPC の 1 枚は伏せる) */
    val initialCards: Int = 2,
    /** カードの数字の範囲(6.2.1節: 1〜10) */
    val minValue: Int = 1,
    val maxValue: Int = 10,
)

object Decks {
    /** 5 属性 × 1〜10 の 50 枚(6.2.1節)。ラウンドごとに全 50 枚を切り直して使う(暫定) */
    fun standard(config: CardConfig = CardConfig()): List<Card> =
        Attribute.entries.flatMap { a -> (config.minValue..config.maxValue).map { Card(a, it) } }

    fun shuffled(random: Random = Random.Default, config: CardConfig = CardConfig()): ArrayDeque<Card> =
        ArrayDeque(standard(config).shuffled(random))
}
