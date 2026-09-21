package com.nebu965039.monsterraising.data

import android.content.Context
import com.nebu965039.monsterraising.core.minigame.Equipment
import com.nebu965039.monsterraising.core.pet.PetConfig

/** 装着中の装備(8.4節)を反映した、育成の調整値。満腹度・清潔度の減少速度が装備で変わる。 */
fun careConfig(context: Context): PetConfig =
    Equipment.applyTo(PetConfig(), MiniGameStore(context.applicationContext).load().inventory.equipEffect())
