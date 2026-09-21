package com.nebu965039.monsterraising.ui.minigame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.minigame.ClearRewards
import com.nebu965039.monsterraising.core.minigame.ItemType
import com.nebu965039.monsterraising.core.minigame.MiniGameProgress
import com.nebu965039.monsterraising.core.minigame.PlayStatus

/** 各ミニゲーム画面で共通の表示部品(プレイ回数・持ち物・クリア報酬)。 */

fun itemName(item: ItemType): String = when (item) {
    ItemType.RICE -> "ごはん"
    ItemType.MINIGAME_TICKET -> "ミニゲーム券"
    ItemType.ELIXIR -> "長寿の秘薬"
    ItemType.EQUIP_STRENGTH -> "筋力の装備"
    ItemType.EQUIP_INTELLECT -> "知力の装備"
    ItemType.EQUIP_SATIETY -> "満腹の装備"
    ItemType.EQUIP_CLEANLINESS -> "清潔の装備"
    ItemType.EQUIP_BALANCE_EFFECT -> "バランスの装備(有効度)"
    ItemType.EQUIP_BALANCE_CARE -> "バランスの装備(お世話)"
}

fun rewardsText(r: ClearRewards): String =
    (listOf("探索ポイント ${r.explorationPoints}") + r.items.map { (item, n) -> "${itemName(item)} ×$n" }).joinToString(" / ")

fun inventoryText(p: MiniGameProgress): String {
    val inv = p.inventory
    val equipment = ItemType.entries.filter { it.name.startsWith("EQUIP_") && inv.count(it) > 0 }
        .joinToString(" ") { "${itemName(it)}×${inv.count(it)}" }
    return "所持: 探索ポイント ${inv.explorationPoints} / ごはん ${inv.count(ItemType.RICE)} / ミニゲーム券 ${inv.count(ItemType.MINIGAME_TICKET)}" +
        if (equipment.isNotEmpty()) " / $equipment" else ""
}

/**
 * 今日のプレイ回数・持ち物の表示と、開始ボタン(2.2節)。
 * 回数を使い切っている場合は、リワード広告の視聴(Phase 8 までデモ)かミニゲーム券のどちらかで 1 回追加できる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayLimitPanel(
    status: PlayStatus,
    progress: MiniGameProgress,
    startEnabled: Boolean,
    onStart: () -> Unit,
    onWatchAd: () -> Unit,
    onUseTicket: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "今日のプレイ ${status.playsToday} / ${status.allowedPlays} 回(残り ${status.remaining} 回)",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(inventoryText(progress), style = MaterialTheme.typography.bodySmall)
        if (status.remaining > 0) {
            Button(enabled = startEnabled, onClick = onStart) { Text("スタート") }
        } else {
            Text("今日の回数を使い切りました。広告を見るか、ミニゲーム券を使うと 1 回遊べます。", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = status.canWatchAd, onClick = onWatchAd) { Text("広告を見て +1 回(デモ)") }
                OutlinedButton(enabled = status.tickets > 0, onClick = onUseTicket) { Text("ミニゲーム券で +1 回(所持 ${status.tickets})") }
            }
        }
    }
}
