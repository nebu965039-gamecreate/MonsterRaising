package com.nebu965039.monsterraising.core.minigame

/** ミニゲームの種類(基本設計書2.2節。プレイ回数はゲームごとに独立して数える)。 */
enum class MiniGame {
    /** ミニゲーム1: 落ち物パズル(5節) */
    PUZZLE,

    /** ミニゲーム2: カード×NPC戦(6節) */
    CARD,

    /** ミニゲーム3: 壁破りゲーム(7節) */
    WALL_BREAK,
}

/** クリア報酬の段階(8.5節)。3 本のミニゲームで共通。 */
enum class RewardTier { BEGINNER, INTERMEDIATE, ADVANCED }

/** クリア時にもらえるもの(8.5節)。 */
data class ClearRewards(
    val explorationPoints: Int,
    val items: Map<ItemType, Int>,
)

object ClearRewardTable {
    /**
     * クリア報酬(8.5節)。
     *   初級: 探索ポイント 50、ノーマル(ごはん)×1
     *   中級: 探索ポイント 75、ノーマル×1、レア(ミニゲーム券)×1
     *   上級: 探索ポイント 100、ノーマル×1、レア×2
     * 上級クリアのみ、[firstClear](その難易度の初回クリア)のとき、かなりレア(そのゲーム固有の装備アイテム。8.4節)を 1 つ追加する。
     */
    fun rewards(game: MiniGame, tier: RewardTier, firstClear: Boolean): ClearRewards {
        val items = mutableMapOf(ItemType.RICE to 1)
        val points = when (tier) {
            RewardTier.BEGINNER -> 50
            RewardTier.INTERMEDIATE -> {
                items[ItemType.MINIGAME_TICKET] = 1
                75
            }
            RewardTier.ADVANCED -> {
                items[ItemType.MINIGAME_TICKET] = 2
                if (firstClear) items[equipmentOf(game)] = 1
                100
            }
        }
        return ClearRewards(points, items)
    }

    /** 上級の初回クリアで得られる装備(8.4節「入手源との対応関係」) */
    fun equipmentOf(game: MiniGame): ItemType = when (game) {
        MiniGame.PUZZLE -> ItemType.EQUIP_BALANCE_EFFECT
        MiniGame.CARD -> ItemType.EQUIP_INTELLECT
        MiniGame.WALL_BREAK -> ItemType.EQUIP_STRENGTH
    }
}
