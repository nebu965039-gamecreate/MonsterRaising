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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
private val ICON_CIRCLE = 58.dp
private val CURRENT_COLOR = Color(0xFFD9683A)

/**
 * ワールドマップ画面(基本設計書10.4節。ワイヤーフレームに準拠)。拠点(自宅・ゲーム拠点・探索拠点)を
 * 白い丸アイコン(拠点の種類で縁の色を変える)で配置した地図で、拠点をタップすると対応する画面へ移る([onSelect])。
 * 現在地には、育てているキャラクターの小さいアイコンと「ここにいる」の表示を出す。拠点間を結ぶ固定のルートは表示しない。
 * 未解放の拠点は破線の縁+鍵アイコンで表示して、選べない。探索中の拠点には「探索中(残り〜)」を表示する。
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

/** 地図の背景(本番の地図画像ができるまでの仮の描画。ワイヤーフレームの山のシルエットに準拠)。 */
@Composable
private fun MapBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val mountain = Color(0xFF2F4A34)
        fun tri(fx: Float, fy: Float, fw: Float, fh: Float, alpha: Float) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * fx, h * (fy + fh))
                lineTo(w * (fx + fw / 2), h * fy)
                lineTo(w * (fx + fw), h * (fy + fh))
                close()
            }
            drawPath(path, mountain, alpha = alpha)
        }
        tri(-0.03f, 0.12f, 0.18f, 0.12f, 0.35f)
        tri(0.77f, 0.07f, 0.15f, 0.11f, 0.3f)
        tri(0.0f, 0.74f, 0.17f, 0.12f, 0.3f)
    }
}

/** 拠点の種類ごとの縁の色(ワイヤーフレームに準拠)。 */
private fun edgeColor(kind: LocationKind): Color = when (kind) {
    LocationKind.HOME -> CURRENT_COLOR
    LocationKind.GAME_BASE -> Color(0xFF3F6F65)
    LocationKind.EXPLORATION -> Color(0xFF7A6350)
}

@Composable
private fun LocationIcon(loc: MapLocation, badge: String?, isCurrent: Boolean, sprite: LoadedSprite?, onClick: () -> Unit) {
    val edge = edgeColor(loc.kind)
    Column(
        Modifier.width(ICON_WIDTH).clickable(enabled = loc.unlocked, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // アイコン本体と「現在地」マーカーは 1 つの Box にまとめる(マーカーは offset で浮かせるだけにして、
        // 下の Column に余計な高さを持たせない。Column の子として並べると offset ぶんの空白ができてしまう)
        Box(contentAlignment = Alignment.TopCenter) {
            if (loc.unlocked) {
                Box(
                    Modifier
                        .size(ICON_CIRCLE)
                        .background(Color.White, CircleShape)
                        .border(3.dp, edge, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    LocationGlyph(loc.id, edge)
                }
            } else {
                // 未解放の拠点は破線の縁+鍵アイコンで表示して、選べない
                Box(
                    Modifier
                        .size(ICON_CIRCLE - 8.dp)
                        .alpha(0.7f)
                        .background(Color(0xFFE7E0D2), CircleShape)
                        .dashedBorder(Color(0xFFC8BDA6)),
                    contentAlignment = Alignment.Center,
                ) {
                    LockGlyph()
                }
            }
            // 現在地には、育てているキャラクターの小さいアイコン(上)と「ここにいる」(その下、アイコンとの間)を表示する
            if (isCurrent && sprite != null) {
                Box(
                    Modifier.align(Alignment.TopCenter).offset(x = 30.dp, y = (-66).dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(30.dp).background(Color.White, CircleShape).border(2.dp, CURRENT_COLOR, CircleShape),
                        contentAlignment = Alignment.BottomCenter,
                    ) { SpritePlayer(sprite, animation = "idle", scale = 1) }
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-30).dp)
                        .background(CURRENT_COLOR, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("ここにいる", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(
            if (loc.unlocked) loc.displayName else "未解放",
            Modifier.background(Color(0xD9FFFFFF), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3A2A1C),
            textAlign = TextAlign.Center,
        )
        badge?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB3261E), textAlign = TextAlign.Center)
        }
    }
}

/** 破線の丸い縁(未解放の拠点用)。 */
private fun Modifier.dashedBorder(color: Color): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        val strokeWidth = 3.dp.toPx()
        drawCircle(
            color,
            radius = (size.minDimension - strokeWidth) / 2f,
            style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
        )
    },
)

/** 拠点の種類・場所ごとの、線画アイコン(本番のドット絵ができるまでの仮のアイコン)。 */
@Composable
private fun LocationGlyph(id: MapLocationId, color: Color) {
    Canvas(Modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (id) {
            MapLocationId.HOME -> {
                val roof = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.1f, h * 0.48f)
                    lineTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.9f, h * 0.48f)
                }
                drawPath(roof, color, style = stroke)
                drawRect(color, topLeft = Offset(w * 0.22f, h * 0.42f), size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.46f), style = stroke)
            }
            MapLocationId.GAME_BASE -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.06f, h * 0.32f),
                    size = androidx.compose.ui.geometry.Size(w * 0.88f, h * 0.44f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.26f, h * 0.54f), Offset(w * 0.4f, h * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.33f, h * 0.47f), Offset(w * 0.33f, h * 0.61f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, w * 0.045f, Offset(w * 0.68f, h * 0.46f))
                drawCircle(color, w * 0.045f, Offset(w * 0.78f, h * 0.58f))
            }
            MapLocationId.CAVE -> {
                val arch = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.14f, h * 0.85f)
                    lineTo(w * 0.14f, h * 0.5f)
                    cubicTo(w * 0.14f, h * 0.18f, w * 0.86f, h * 0.18f, w * 0.86f, h * 0.5f)
                    lineTo(w * 0.86f, h * 0.85f)
                }
                drawPath(arch, color, style = stroke)
            }
            MapLocationId.COAST -> {
                for (row in 0..1) {
                    val y = h * (0.42f + row * 0.24f)
                    val wave = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.08f, y)
                        cubicTo(w * 0.24f, y - h * 0.1f, w * 0.38f, y + h * 0.1f, w * 0.5f, y)
                        cubicTo(w * 0.62f, y - h * 0.1f, w * 0.76f, y + h * 0.1f, w * 0.92f, y)
                    }
                    drawPath(wave, color, style = stroke)
                }
            }
            MapLocationId.MOUNTAIN -> {
                // 「森」の木のアイコン(名残の識別子。core/map/WorldMap.kt のコメント参照)
                val tree = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, h * 0.1f)
                    lineTo(w * 0.78f, h * 0.55f)
                    lineTo(w * 0.6f, h * 0.55f)
                    lineTo(w * 0.85f, h * 0.85f)
                    lineTo(w * 0.15f, h * 0.85f)
                    lineTo(w * 0.4f, h * 0.55f)
                    lineTo(w * 0.22f, h * 0.55f)
                    close()
                }
                drawPath(tree, color)
                drawLine(color, Offset(w * 0.5f, h * 0.85f), Offset(w * 0.5f, h * 0.95f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

/** 未解放の拠点に出す、鍵のアイコン。 */
@Composable
private fun LockGlyph() {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val color = Color(0xFF8A7660)
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.14f, h * 0.46f),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.44f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            style = stroke,
        )
        val shackle = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.28f, h * 0.46f)
            lineTo(w * 0.28f, h * 0.3f)
            cubicTo(w * 0.28f, h * 0.08f, w * 0.72f, h * 0.08f, w * 0.72f, h * 0.3f)
            lineTo(w * 0.72f, h * 0.46f)
        }
        drawPath(shackle, color, style = stroke)
    }
}
