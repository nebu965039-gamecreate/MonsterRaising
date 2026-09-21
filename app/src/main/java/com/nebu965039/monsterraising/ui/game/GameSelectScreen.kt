package com.nebu965039.monsterraising.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.minigame.DayClock
import com.nebu965039.monsterraising.core.minigame.MiniGame
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.ui.demo.DemoClock
import kotlinx.coroutines.delay

/** ゲームの名前・説明(ゲーム選択画面用) */
fun MiniGame.title(): String = when (this) {
    MiniGame.PUZZLE -> "落ち物パズル"
    MiniGame.CARD -> "カード × NPC 戦"
    MiniGame.WALL_BREAK -> "壁破りゲーム"
}

private fun MiniGame.summary(): String = when (this) {
    MiniGame.PUZZLE -> "ブロックを積んでラインを消す 90 秒のタイムアタック。知力系・筋力系の有効度が上がる"
    MiniGame.CARD -> "合計を 20 に近づけるコインの対戦。勝つと知力系の有効度が上がる"
    MiniGame.WALL_BREAK -> "指令に合う色の壁を選んで破壊する 60 秒のタイムアタック。筋力系の有効度が上がる"
}

/**
 * ゲーム選択画面(基本設計書10.1節)。ワールドマップのゲーム拠点をタップすると開く。
 * 遊ぶミニゲームを選ぶ([onSelect])。今日の残りのプレイ回数(2.2節)も表示する。
 */
@Composable
fun GameSelectScreen(onSelect: (MiniGame) -> Unit) {
    val context = LocalContext.current
    val store = remember { MiniGameStore(context) }
    val petStore = remember { PetStore(context) }
    var progress by remember { mutableStateOf(store.load()) }
    // 遊び終えて戻ってきたときなどに、残り回数を読み直す
    LaunchedEffect(Unit) {
        while (true) {
            progress = store.load()
            delay(1_000)
        }
    }
    val isEgg = petStore.load()?.stage == Stage.EGG
    val day = DayClock.dayIndex(DemoClock.now())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("遊ぶゲームを選んでください。", style = MaterialTheme.typography.bodyMedium)
        if (isEgg) Text("卵の間は遊べません。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        MiniGame.entries.forEach { game ->
            val status = progress.playStatus(game, day)
            Card(onClick = { onSelect(game) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(game.title(), style = MaterialTheme.typography.titleMedium)
                    Text(game.summary(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "今日のプレイ ${status.playsToday} / ${status.allowedPlays} 回(残り ${status.remaining} 回)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
