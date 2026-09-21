package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.core.pet.PetConfig

/** 装備の装着枠(基本設計書8.4.1節)。同じ枠には 1 つだけ装着できる。 */
enum class EquipSlot { EFFECT, CARE }

/** 装備が持つ効果。値は割合(0.05 = 5%)。 */
data class EquipEffect(
    /** 知力系有効度の増加量への上乗せ */
    val intellectGain: Double = 0.0,
    /** 筋力系有効度の増加量への上乗せ */
    val strengthGain: Double = 0.0,
    /** 満腹度の減少速度の軽減 */
    val satietyDecayCut: Double = 0.0,
    /** 清潔度の減少速度の軽減 */
    val cleanlinessDecayCut: Double = 0.0,
) {
    operator fun plus(o: EquipEffect) = EquipEffect(
        intellectGain + o.intellectGain,
        strengthGain + o.strengthGain,
        satietyDecayCut + o.satietyDecayCut,
        cleanlinessDecayCut + o.cleanlinessDecayCut,
    )
}

/** 装備アイテム 6 種のカタログ(8.4節)。 */
object Equipment {
    private val catalog: Map<ItemType, Pair<EquipSlot, EquipEffect>> = mapOf(
        ItemType.EQUIP_STRENGTH to (EquipSlot.EFFECT to EquipEffect(strengthGain = 0.05)),
        ItemType.EQUIP_INTELLECT to (EquipSlot.EFFECT to EquipEffect(intellectGain = 0.05)),
        ItemType.EQUIP_BALANCE_EFFECT to (EquipSlot.EFFECT to EquipEffect(intellectGain = 0.025, strengthGain = 0.025)),
        ItemType.EQUIP_SATIETY to (EquipSlot.CARE to EquipEffect(satietyDecayCut = 0.05)),
        ItemType.EQUIP_CLEANLINESS to (EquipSlot.CARE to EquipEffect(cleanlinessDecayCut = 0.05)),
        ItemType.EQUIP_BALANCE_CARE to (EquipSlot.CARE to EquipEffect(satietyDecayCut = 0.025, cleanlinessDecayCut = 0.025)),
    )

    fun isEquipment(item: ItemType): Boolean = item in catalog

    fun all(): List<ItemType> = catalog.keys.toList()

    fun slotOf(item: ItemType): EquipSlot? = catalog[item]?.first

    fun effectOf(item: ItemType): EquipEffect? = catalog[item]?.second

    /** 装着中の装備すべての効果の合計 */
    fun effectOf(equipped: Collection<ItemType>): EquipEffect =
        equipped.mapNotNull { effectOf(it) }.fold(EquipEffect()) { a, b -> a + b }

    /** 装着中の装備の効果を、育成の調整値(満腹度・清潔度の減少速度)へ反映する */
    fun applyTo(config: PetConfig, effect: EquipEffect): PetConfig = config.copy(
        satietyDecayPerHour = config.satietyDecayPerHour * (1.0 - effect.satietyDecayCut),
        cleanlinessDecayPerHour = config.cleanlinessDecayPerHour * (1.0 - effect.cleanlinessDecayCut),
    )

    /** 効果の説明文 */
    fun describe(item: ItemType): String {
        val e = effectOf(item) ?: return ""
        fun pct(v: Double) = "%.1f".format(v * 100).removeSuffix(".0")
        return buildList {
            if (e.strengthGain > 0) add("筋力系有効度 +${pct(e.strengthGain)}%")
            if (e.intellectGain > 0) add("知力系有効度 +${pct(e.intellectGain)}%")
            if (e.satietyDecayCut > 0) add("満腹度の減少速度 -${pct(e.satietyDecayCut)}%")
            if (e.cleanlinessDecayCut > 0) add("清潔度の減少速度 -${pct(e.cleanlinessDecayCut)}%")
        }.joinToString("、")
    }
}
