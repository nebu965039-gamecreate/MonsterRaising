package com.nebu965039.monsterraising.core.pet

/** 進化段階と各段階の滞在期間(基本設計書4.3)。 */
enum class Stage(val durationMs: Long) {
    EGG(1 * MS_MINUTE),
    INFANT(1 * MS_DAY),
    GROWTH_1(3 * MS_DAY),
    GROWTH_2(7 * MS_DAY),

    /** 成熟期。これ以上の進化はない(2週間は進化条件ではなく寿命。4.5節) */
    MATURE(14 * MS_DAY),
    ;

    fun next(): Stage? = entries.getOrNull(ordinal + 1)
}

private const val MS_MINUTE = 60_000L
private const val MS_DAY = 24 * 60 * MS_MINUTE
