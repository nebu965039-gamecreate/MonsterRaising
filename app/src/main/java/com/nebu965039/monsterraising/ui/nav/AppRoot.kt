package com.nebu965039.monsterraising.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.exploration.Exploration
import com.nebu965039.monsterraising.core.exploration.ExplorationConfig
import com.nebu965039.monsterraising.core.map.Destination
import com.nebu965039.monsterraising.core.minigame.DayClock
import com.nebu965039.monsterraising.core.minigame.MiniGame
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.ui.card.CardScreen
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.ui.demo.PetDemoScreen
import com.nebu965039.monsterraising.ui.dev.DevScreen
import com.nebu965039.monsterraising.ui.game.GameSelectScreen
import com.nebu965039.monsterraising.ui.game.title
import com.nebu965039.monsterraising.ui.map.ExplorationSiteScreen
import com.nebu965039.monsterraising.ui.map.WorldMapScreen
import com.nebu965039.monsterraising.ui.puzzle.PuzzleScreen
import com.nebu965039.monsterraising.ui.wallbreak.WallBreakScreen
import kotlinx.coroutines.delay

/** 画面の上部に出す画面名 */
private fun Destination.title(): String = when (this) {
    Destination.Home -> ""
    Destination.WorldMap -> "ワールドマップ"
    Destination.GameSelect -> "ゲーム拠点"
    is Destination.Game -> game.title()
    is Destination.Site -> site.displayName
    Destination.Dev -> "開発用"
}

/**
 * アプリの画面遷移(基本設計書10.1節・10.2節・10.4節)。
 * メイン画面(育成画面)を起点に、ヘッダーの地図アイコンからワールドマップへ移り、拠点をタップして
 * ゲーム拠点(ゲーム選択 → 各ミニゲーム)や探索拠点の画面へ遷移する。常時表示のボトムナビゲーションは置かない。
 * 戻る操作([Destination.parent])で、ひとつ前の画面へ戻る。
 *
 * ログインボーナス(探索ポイント +100・ごはん +3。1 日 1 回)と、はじめてのプレゼント(ごはん 10 個。一度だけ)も、
 * ここで、アプリを開いたとき・日付が変わったときに受け取る。
 */
@Composable
fun AppRoot(resumeTick: Int) {
    val context = LocalContext.current
    var destKey by rememberSaveable { mutableStateOf(Destination.Home.key) }
    val dest = Destination.fromKey(destKey) ?: Destination.Home
    fun go(d: Destination) {
        destKey = d.key
    }
    var loginNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resumeTick) {
        val store = MiniGameStore(context.applicationContext)
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

    // 戻る操作: ひとつ前の画面へ(メイン画面では、通常どおりアプリを閉じる)
    BackHandler(enabled = dest.parent != null) { dest.parent?.let { go(it) } }

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
        if (dest == Destination.Home) {
            HomeHeader(onOpenMap = { go(Destination.WorldMap) }, onOpenDev = { go(Destination.Dev) })
        } else {
            ScreenTopBar(dest.title(), onBack = { dest.parent?.let { go(it) } })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val d = dest) {
                Destination.Home -> PetDemoScreen(resumeTick = resumeTick)
                Destination.WorldMap -> WorldMapScreen(onSelect = { go(Destination.forLocation(it)) })
                Destination.GameSelect -> GameSelectScreen(onSelect = { go(Destination.Game(it)) })
                is Destination.Game -> when (d.game) {
                    MiniGame.PUZZLE -> PuzzleScreen()
                    MiniGame.CARD -> CardScreen()
                    MiniGame.WALL_BREAK -> WallBreakScreen()
                }
                is Destination.Site -> ExplorationSiteScreen(d.site, onLeave = { go(Destination.WorldMap) })
                Destination.Dev -> DevScreen()
            }
        }
    }
}
