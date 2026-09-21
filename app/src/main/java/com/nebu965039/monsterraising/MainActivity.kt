package com.nebu965039.monsterraising

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.exploration.Exploration
import com.nebu965039.monsterraising.core.exploration.ExplorationConfig
import com.nebu965039.monsterraising.core.minigame.DayClock
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.ui.card.CardScreen
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.ui.demo.DemoScreen
import com.nebu965039.monsterraising.ui.demo.PetDemoScreen
import com.nebu965039.monsterraising.ui.map.MapScreen
import com.nebu965039.monsterraising.ui.puzzle.PuzzleScreen
import com.nebu965039.monsterraising.ui.wallbreak.WallBreakScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    /** 前面に戻るたびに増える。ウィジェットで操作された状態を画面に読み直させるために使う */
    private var resumeTick by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    var tab by rememberSaveable { mutableIntStateOf(0) }
                    var loginNotice by remember { mutableStateOf<String?>(null) }
                    // ログインボーナス(1 日 1 回: 探索ポイントとごはん。8.2節)。アプリを開いたとき、日付が変わったときに受け取る
                    LaunchedEffect(resumeTick) {
                        val store = MiniGameStore(applicationContext)
                        val bonus = ExplorationConfig()
                        while (true) {
                            val gift = store.update { p -> Exploration.claimStartingGift(p, bonus) }
                            val got = store.update { p -> Exploration.claimLoginBonus(p, DayClock.dayIndex(DemoClock.now()), bonus) }
                            val messages = buildList {
                                if (gift) add("はじめてのプレゼント: ごはん ${bonus.startingRice} 個")
                                if (got) add("ログインボーナス: 探索ポイント +${bonus.loginBonus} / ごはん +${bonus.loginRice}")
                            }
                            if (messages.isNotEmpty()) loginNotice = messages.joinToString("\n")
                            delay(30_000)
                        }
                    }
                    Column(Modifier.safeDrawingPadding()) {
                        loginNotice?.let {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { loginNotice = null }) { Text("OK") }
                            }
                        }
                        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("育成") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("パズル") })
                            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("カード") })
                            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("壁破り") })
                            Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("マップ") })
                            Tab(selected = tab == 5, onClick = { tab = 5 }, text = { Text("アニメ") })
                        }
                        when (tab) {
                            0 -> PetDemoScreen(resumeTick = resumeTick)
                            1 -> PuzzleScreen()
                            2 -> CardScreen()
                            3 -> WallBreakScreen()
                            4 -> MapScreen()
                            else -> DemoScreen()
                        }
                    }
                }
            }
        }
    }
}
