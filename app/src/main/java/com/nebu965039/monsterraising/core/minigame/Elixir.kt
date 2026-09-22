package com.nebu965039.monsterraising.core.minigame

import com.nebu965039.monsterraising.core.pet.PetConfig
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.Stage

/** 「長寿の秘薬」を使った結果。 */
sealed interface ElixirUse {
    /** 使えた。[progress] は 1 個減った持ち物、[pet] は寿命が延びた育成状態 */
    data class Used(val progress: MiniGameProgress, val pet: PetState) : ElixirUse

    /** 秘薬を持っていない */
    data object NoElixir : ElixirUse

    /** 成熟期ではない(4.5節: 成熟期のみ有効)。秘薬は減らさない。[pet] は経過時間を反映した状態 */
    data class NotMature(val pet: PetState) : ElixirUse
}

/** 持ち物からの「長寿の秘薬」の使用(基本設計書4.5節)。 */
object Elixir {
    /**
     * 秘薬を 1 個使い、寿命を 1 週間延ばす。経過時間を反映した結果、すでに寿命・死亡を迎えて卵になっていた場合は、
     * 成熟期ではないので使えない(秘薬は減らない)。
     */
    fun use(progress: MiniGameProgress, pet: PetState, nowMs: Long, config: PetConfig = PetConfig()): ElixirUse {
        val settled = PetSimulator.advance(pet, nowMs, config)
        if (settled.stage != Stage.MATURE) return ElixirUse.NotMature(settled)
        val inventory = progress.inventory.consume(ItemType.ELIXIR) ?: return ElixirUse.NoElixir
        return ElixirUse.Used(
            progress.copy(inventory = inventory),
            PetSimulator.extendLifespan(settled, nowMs, config),
        )
    }
}
