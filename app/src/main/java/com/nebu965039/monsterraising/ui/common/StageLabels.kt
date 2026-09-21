package com.nebu965039.monsterraising.ui.common

import com.nebu965039.monsterraising.core.pet.Stage

/** 進化段階の表示名(4.3節)。本体アプリとウィジェットで共通。 */
fun Stage.label(): String = when (this) {
    Stage.EGG -> "卵"
    Stage.INFANT -> "幼年期"
    Stage.GROWTH_1 -> "成長期Ⅰ"
    Stage.GROWTH_2 -> "成長期Ⅱ"
    Stage.MATURE -> "成熟期"
}
