package com.nebu965039.monsterraising.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebu965039.monsterraising.core.exploration.Exploration
import com.nebu965039.monsterraising.core.exploration.ExplorationConfig
import com.nebu965039.monsterraising.core.exploration.ExplorationStatus
import com.nebu965039.monsterraising.core.map.LocationKind
import com.nebu965039.monsterraising.core.map.MapLocation
import com.nebu965039.monsterraising.core.map.MapLocationId
import com.nebu965039.monsterraising.core.map.WorldMap
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.ui.demo.DemoFriends
import com.nebu965039.monsterraising.ui.sprite.LoadedSprite
import com.nebu965039.monsterraising.ui.sprite.SpriteAssets
import com.nebu965039.monsterraising.ui.sprite.SpritePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val SEA = Color(0xFFBFE3F5)
private val ICON_WIDTH = 96.dp
private val ICON_CIRCLE = 60.dp

/**
 * ワールドマップ画面(基本設計書10.4節)。拠点(自宅・ゲーム拠点・探索拠点)をアイコンで配置した地図で、
 * 拠点をタップすると、その拠点に対応する画面へ移る([onSelect])。
 * 現在地には、育てているキャラクターの小さいアイコンを表示する。拠点間を結ぶ固定のルートは表示しない。
 * 未解放の拠点はグレーアウトして、選べない。探索中の拠点には「探索中(残り〜)」を表示する。
 */
@Composable
fun WorldMapScreen(onSelect: (MapLocationId) -> Unit) {
    val context = LocalContext.current
    val store = remember { MiniGameStore(context) }
    var progress by remember { mutableStateOf(store.load()) }
    var nowMs by remember { mutableLongStateOf(DemoClock.now()) }
    val sprite by produceState<LoadedSprite?>(null) {
        value = withContext(Dispatchers.IO) { SpriteAssets.load(context, "fox") }
    }
    // 残り時間の表示・ほかの画面で変わった持ち物を読み直す
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = DemoClock.now()
            progress = store.load()
            delay(1_000)
        }
    }

    val overview = Exploration.overview(progress, nowMs)
    val current = WorldMap.currentLocation(overview)
    val capacity = ExplorationConfig().capacity(DemoFriends.hasFriends)

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("探索ポイント ${progress.inventory.explorationPoints}", style = MaterialTheme.typography.titleMedium)
            Text("同時探索 ${progress.exploration.actives.size} / $capacity", style = MaterialTheme.typography.bodyMedium)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SEA)) {
            val density = LocalDensity.current
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val halfIconWidth = with(density) { (ICON_WIDTH / 2).toPx() }
            val circleRadius = with(density) { (ICON_CIRCLE / 2).toPx() }

            MapBackground()
            for (loc in WorldMap.locations()) {
                val badge = badgeOf(loc, overview.inProgress, overview.finished)
                Box(
                    Modifier.offset {
                        IntOffset(
                            (loc.x * widthPx - halfIconWidth).roundToInt(),
                            (loc.y * heightPx - circleRadius).roundToInt(),
                        )
                    },
                ) {
                    LocationIcon(loc, badge, isCurrent = loc.id == current, sprite = sprite, onClick = { onSelect(loc.id) })
                }
            }
        }
        Text("拠点をタップして移動します。", style = MaterialTheme.typography.bodySmall)
    }
}

/** 探索拠点に出す、探索の状況の表示(探索中・完了) */
private fun badgeOf(loc: MapLocation, inProgress: List<ExplorationStatus.InProgress>, finished: List<ExplorationStatus.Finished>): String? {
    val site = loc.site ?: return null
    inProgress.firstOrNull { it.site == site }?.let { return "探索中(残り ${Exploration.remainingText(it.remainingMs)})" }
    if (finished.any { it.site == site }) return "探索完了!"
    return null
}

/** 地図の背景(本番の地図画像ができるまでの仮の描画)。拠点ごとに地形の色を変える。 */
@Composable
private fun MapBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val m = minOf(size.width, size.height)
        fun blob(color: Color, fx: Float, fy: Float, r: Float) = drawCircle(color, m * r, Offset(size.width * fx, size.height * fy))
        blob(Color(0xFFD3E8B5), 0.50f, 0.58f, 0.36f) // 陸地(草原)
        blob(Color(0xFFC6E0A0), 0.28f, 0.72f, 0.24f) // 自宅のあたり
        blob(Color(0xFFE4E0B8), 0.74f, 0.70f, 0.22f) // ゲーム拠点のあたり
        blob(Color(0xFFB7AE9C), 0.20f, 0.26f, 0.16f) // 洞窟のあたり
        blob(Color(0xFF9DB58A), 0.50f, 0.12f, 0.15f) // 山のあたり
        blob(Color(0xFFF1DFA8), 0.80f, 0.30f, 0.15f) // 海岸のあたり
    }
}

@Composable
private fun LocationIcon(loc: MapLocation, badge: String?, isCurrent: Boolean, sprite: LoadedSprite?, onClick: () -> Unit) {
    val edge = when (loc.kind) {
        LocationKind.HOME -> Color(0xFF4CAF50)
        LocationKind.GAME_BASE -> Color(0xFFF59A2B)
        LocationKind.EXPLORATION -> Color(0xFF3F7FE0)
    }
    Column(
        Modifier.width(ICON_WIDTH).clickable(enabled = loc.unlocked, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 未解放の拠点はグレーアウトして、鍵を表示する
        Box(
            Modifier
                .size(ICON_CIRCLE)
                .alpha(if (loc.unlocked) 1f else 0.4f)
                .background(if (loc.unlocked) Color.White else Color(0xFFBDBDBD), CircleShape)
                .border(3.dp, if (loc.unlocked) edge else Color(0xFF757575), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (loc.unlocked) loc.icon else "🔒", fontSize = 28.sp)
        }
        // 現在地には、育てているキャラクターの小さいアイコンを表示する
        if (isCurrent && sprite != null) {
            Box(Modifier.offset(x = 30.dp, y = (-ICON_CIRCLE - 6.dp))) {
                SpritePlayer(sprite, animation = "idle", scale = 1)
            }
        }
        Text(
            loc.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (loc.unlocked) Color(0xFF2B1B12) else Color(0xFF757575),
            textAlign = TextAlign.Center,
        )
        badge?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB3261E), textAlign = TextAlign.Center)
        }
    }
}
