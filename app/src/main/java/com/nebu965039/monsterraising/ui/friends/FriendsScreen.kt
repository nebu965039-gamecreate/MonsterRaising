package com.nebu965039.monsterraising.ui.friends

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.friends.Friendship
import com.nebu965039.monsterraising.core.minigame.DayClock
import com.nebu965039.monsterraising.core.minigame.MiniGame
import com.nebu965039.monsterraising.data.CardRecordStore
import com.nebu965039.monsterraising.data.PuzzleRecordStore
import com.nebu965039.monsterraising.data.ScoreRecordStore
import com.nebu965039.monsterraising.data.friendshipStore
import com.nebu965039.monsterraising.minigame.puzzle.Difficulty
import com.nebu965039.monsterraising.minigame.wallbreak.WallDifficulty
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.ui.demo.DemoFriends
import com.nebu965039.monsterraising.ui.game.title
import kotlinx.coroutines.delay

private fun levelName(name: String) = when (name) {
    "BEGINNER" -> "初級"
    "INTERMEDIATE" -> "中級"
    else -> "上級"
}

/**
 * フレンド画面(基本設計書9節)の骨組み。サーバー(Firebase。Phase 6・7)ができるまでは、
 * フレンドの追加・実際のフレンドの一覧・ランキングは使えない。画面の構成と、端末内でできる部分だけ動く:
 * - フレンドシップポイント(進化のフレンドルートに使う。4.3節)
 * - フレンド一覧と「いいね」(動作確認用の仮のフレンドで確認できる。開発用画面のスイッチ)
 * - 自己ベスト(フレンド限定ランキングで共有する値。9.1節)
 */
@Composable
fun FriendsScreen() {
    val context = LocalContext.current
    val store = remember { friendshipStore(context) }
    var state by remember { mutableStateOf(store.load()) }
    var nowMs by remember { mutableLongStateOf(DemoClock.now()) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = DemoClock.now()
            delay(1_000)
        }
    }
    val day = DayClock.dayIndex(nowMs)
    val friends = DemoFriends.friends(nowMs)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("フレンドシップポイント  ${state.points} P", style = MaterialTheme.typography.titleMedium)
                Text(
                    "フレンドとの交流で貯まります(いいね: 送ると 1 日 1 回 100P、もらうと 1 回ごとに 100P)。10,000P・100,000P で、進化の特別ルートが解放されます。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text("フレンド(${friends.size} 人)", style = MaterialTheme.typography.titleMedium)
        if (friends.isEmpty()) {
            Text(
                "まだフレンドがいません。フレンドの追加は、サーバー機能の実装後に使えるようになります(準備中)。",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            friends.forEach { friend ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(friend.name, style = MaterialTheme.typography.bodyLarge)
                        Text("最終ログイン: ${Friendship.lastLoginText(nowMs, friend.lastLoginMs)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        enabled = !state.hasLiked(friend.id, day),
                        onClick = {
                            val result = Friendship.sendLike(state, friend.id, day)
                            if (result != null) {
                                state = store.update { result.state }
                                message = if (result.pointsGained > 0) "いいねを送りました(+${result.pointsGained}P)" else "いいねを送りました(ポイントは 1 日 1 回まで)"
                            }
                        },
                    ) { Text(if (state.hasLiked(friend.id, day)) "送信済み" else "いいね") }
                }
                HorizontalDivider()
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        OutlinedButton(enabled = false, onClick = {}) { Text("フレンドを追加(準備中)") }

        Text("ランキング", style = MaterialTheme.typography.titleMedium)
        Text(
            "各ミニゲームの自己ベストを、フレンドと共有してランキングにする予定です(準備中)。いまの自分の記録:",
            style = MaterialTheme.typography.bodySmall,
        )
        BestScores()
    }
}

/** 自分の自己ベスト(フレンド限定ランキングで共有する値。9.1節) */
@Composable
private fun BestScores() {
    val context = LocalContext.current
    val puzzle = remember { PuzzleRecordStore(context).load() }
    val wall = remember { ScoreRecordStore(context, "wallbreak_records.json").load() }
    val card = remember { CardRecordStore(context).load() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${MiniGame.PUZZLE.title()}   " + Difficulty.entries.joinToString(" / ") { "${levelName(it.name)} ${puzzle.highScore(it)}" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${MiniGame.WALL_BREAK.title()}   " + WallDifficulty.entries.joinToString(" / ") { "${levelName(it.name)} ${wall.highScore(it.name)}" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("${MiniGame.CARD.title()}   連勝の最高 ${card.bestStreak}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
