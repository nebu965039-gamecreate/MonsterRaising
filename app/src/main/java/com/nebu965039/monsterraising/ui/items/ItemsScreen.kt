package com.nebu965039.monsterraising.ui.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.minigame.Elixir
import com.nebu965039.monsterraising.core.minigame.ElixirUse
import com.nebu965039.monsterraising.core.minigame.EquipSlot
import com.nebu965039.monsterraising.core.minigame.Equipment
import com.nebu965039.monsterraising.core.minigame.ItemType
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.data.careConfig
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.ui.minigame.itemName
import com.nebu965039.monsterraising.widget.PetWidgetUpdater
import kotlinx.coroutines.delay

private const val DAY_MS = 24 * 3_600_000L

/**
 * 持ち物画面(基本設計書10.2.5節)。
 * - 持ち物の一覧(探索ポイント・ごはん・ミニゲーム券)
 * - 長寿の秘薬の使用(4.5節。成熟期のみ。1 個で寿命が 1 週間延びる)
 * - 装備の装着・取り外し(8.4節)。装備は消費せず、有効度の枠・お世話の枠にそれぞれ 1 つ装着できる
 */
@Composable
fun ItemsScreen() {
    val context = LocalContext.current.applicationContext
    val store = remember { MiniGameStore(context) }
    val petStore = remember { PetStore(context) }
    var progress by remember { mutableStateOf(store.load()) }
    var pet by remember { mutableStateOf(petStore.load()) }
    var message by remember { mutableStateOf<String?>(null) }
    // ほかの画面で変わった持ち物・育成状態を読み直す
    LaunchedEffect(Unit) {
        while (true) {
            progress = store.load()
            pet = petStore.load()
            delay(1_000)
        }
    }
    val inventory = progress.inventory
    val now = DemoClock.now()

    /** 装備を変える前に、いまの装備での減少を反映する(装備の効果を、変える前の期間へさかのぼらせない) */
    fun changeEquipment(change: (com.nebu965039.monsterraising.core.minigame.Inventory) -> com.nebu965039.monsterraising.core.minigame.Inventory?) {
        val at = DemoClock.now()
        val oldConfig = careConfig(context)
        petStore.load()?.let { petStore.update(at, oldConfig) { s -> PetSimulator.advance(s, at, oldConfig) } }
        progress = store.update { p ->
            val next = change(p.inventory)?.let { p.copy(inventory = it) } ?: p
            next to next
        }
        pet = petStore.load()
        PetWidgetUpdater.updateAll(context)
    }

    fun useElixir() {
        val at = DemoClock.now()
        val config = careConfig(context)
        var result: ElixirUse = ElixirUse.NoElixir
        petStore.update(at, config) { current ->
            store.update { p ->
                val r = Elixir.use(p, current, at, config)
                result = r
                (if (r is ElixirUse.Used) r.progress else p) to r
            }
            when (val r = result) {
                is ElixirUse.Used -> r.pet
                is ElixirUse.NotMature -> r.pet
                ElixirUse.NoElixir -> current
            }
        }
        message = when (result) {
            is ElixirUse.Used -> "長寿の秘薬を使いました。寿命が 1 週間延びました。"
            is ElixirUse.NotMature -> "長寿の秘薬は、成熟期のキャラクターにだけ使えます。"
            ElixirUse.NoElixir -> "長寿の秘薬を持っていません。"
        }
        progress = store.load()
        pet = petStore.load()
        PetWidgetUpdater.updateAll(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("持ち物", style = MaterialTheme.typography.titleMedium)
        Text(
            "探索ポイント ${inventory.explorationPoints} / ごはん ${inventory.count(ItemType.RICE)} / " +
                "ミニゲーム券 ${inventory.count(ItemType.MINIGAME_TICKET)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider()

        Text("長寿の秘薬", style = MaterialTheme.typography.titleMedium)
        val elixirs = inventory.count(ItemType.ELIXIR)
        Text("所持 $elixirs 個。成熟期のキャラクターに使うと、寿命が 1 週間延びます(複数個で積み上がります)。", style = MaterialTheme.typography.bodySmall)
        pet?.let { Text(lifespanText(it, now), style = MaterialTheme.typography.bodySmall) }
        val canUse = elixirs > 0 && pet?.stage == Stage.MATURE
        Button(enabled = canUse, onClick = { useElixir() }) { Text("使う") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        HorizontalDivider()

        Text("装備", style = MaterialTheme.typography.titleMedium)
        Text(
            "装備は使っても減りません。「有効度」の枠と「お世話」の枠に、それぞれ 1 つずつ装着できます。",
            style = MaterialTheme.typography.bodySmall,
        )
        for (slot in EquipSlot.entries) {
            Text(if (slot == EquipSlot.EFFECT) "有効度の枠" else "お世話の枠", style = MaterialTheme.typography.labelLarge)
            for (item in Equipment.all().filter { Equipment.slotOf(it) == slot }) {
                EquipmentCard(
                    item = item,
                    owned = inventory.count(item),
                    worn = inventory.isEquipped(item),
                    onToggle = { changeEquipment { inv -> if (inv.isEquipped(item)) inv.unequip(item) else inv.equip(item) } },
                )
            }
        }
        Text("入手:ミニゲームの上級の初回クリア、または探索(8.4節)", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EquipmentCard(item: ItemType, owned: Int, worn: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${itemName(item)}  ×$owned" + if (worn) "  (装着中)" else "", style = MaterialTheme.typography.bodyMedium)
                Text(Equipment.describe(item), style = MaterialTheme.typography.bodySmall)
            }
            if (worn) {
                OutlinedButton(onClick = onToggle) { Text("外す") }
            } else {
                Button(enabled = owned > 0, onClick = onToggle) { Text("装着") }
            }
        }
    }
}

/** 寿命の表示(成熟期のみ。4.5節) */
private fun lifespanText(pet: PetState, nowMs: Long): String {
    val end = pet.lifespanEndMs() ?: return "いまは成熟期ではありません(寿命の対象は成熟期のみ)。"
    val leftDays = ((end - nowMs).coerceAtLeast(0L) + DAY_MS - 1) / DAY_MS
    val extended = pet.lifespanExtensionMs / (7 * DAY_MS)
    return "寿命まで あと約 $leftDays 日" + if (extended > 0) "(秘薬で ${extended} 週間延長済み)" else ""
}
