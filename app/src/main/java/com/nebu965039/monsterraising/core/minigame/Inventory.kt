package com.nebu965039.monsterraising.core.minigame

import kotlinx.serialization.Serializable

/** アイテムの種類(基本設計書8.3節・8.4節)。 */
enum class ItemType {
    /** ノーマル: ごはん(餌) */
    RICE,

    /** レア: ミニゲーム券。どのミニゲームでも、プレイ回数の上限に達したときに 1 回分を追加できる */
    MINIGAME_TICKET,

    /** 超レア: 長寿の秘薬(4.5節) */
    ELIXIR,

    // かなりレア: 装備アイテム 6 種(8.4節)。効果は [Equipment]
    EQUIP_STRENGTH,
    EQUIP_INTELLECT,
    EQUIP_SATIETY,
    EQUIP_CLEANLINESS,
    EQUIP_BALANCE_EFFECT,
    EQUIP_BALANCE_CARE,
}

/** 持ち物と探索ポイント(8.2節・8.3節)。端末内に保存する。 */
@Serializable
data class Inventory(
    /** 探索ポイント。上限なし(8.2節) */
    val explorationPoints: Int = 0,
    /** アイテム名 → 個数(列挙名をキーにして保存する) */
    val items: Map<String, Int> = emptyMap(),
    /** 装着中の装備(列挙名)。装備は消費せず、所持している間だけ装着できる。装着枠は 1 つ([Equipment]) */
    val equipped: String? = null,
) {
    fun count(item: ItemType): Int = items[item.name] ?: 0

    /** 装着中の装備。所持していない(手放した)場合は装着なし扱い */
    fun equippedItem(): ItemType? = equipped?.let { name -> Equipment.all().find { it.name == name } }?.takeIf { count(it) > 0 }

    fun isEquipped(item: ItemType): Boolean = item == equippedItem()

    /** 装着中の装備の効果 */
    fun equipEffect(): EquipEffect = equippedItem()?.let { Equipment.effectOf(it) } ?: EquipEffect()

    /** [item] を装着する(装着中のものと入れ替わる)。装備でない・所持していなければ null */
    fun equip(item: ItemType): Inventory? {
        if (!Equipment.isEquipment(item)) return null
        if (count(item) <= 0) return null
        return copy(equipped = item.name)
    }

    fun unequip(item: ItemType): Inventory = if (equipped == item.name) copy(equipped = null) else this

    fun add(item: ItemType, amount: Int): Inventory {
        require(amount >= 0)
        return if (amount == 0) this else copy(items = items + (item.name to count(item) + amount))
    }

    fun addPoints(amount: Int): Inventory {
        require(amount >= 0)
        return copy(explorationPoints = explorationPoints + amount)
    }

    /** 探索ポイントを [amount] 使う。足りなければ null(何も変えない) */
    fun spendPoints(amount: Int): Inventory? {
        require(amount >= 0)
        return if (explorationPoints < amount) null else copy(explorationPoints = explorationPoints - amount)
    }

    /** [amount] 個を使う。足りなければ null(何も変えない) */
    fun consume(item: ItemType, amount: Int = 1): Inventory? {
        require(amount >= 0)
        val have = count(item)
        if (have < amount) return null
        return copy(items = items + (item.name to have - amount))
    }
}
