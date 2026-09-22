package com.nebu965039.monsterraising.ui.care

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nebu965039.monsterraising.core.care.StainField
import com.nebu965039.monsterraising.core.sprite.SpriteTimeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPRITE_SOURCE_SIZE = 48
private val STAIN_COLOR = Color(0xFF6B4A2B)
private val PANEL_BG = Color(0xF0FFFFFF)
private val PACK_COLOR = Color(0xFFD9683A)
private val TEXT_DARK = Color(0xFF3A2A1C)

/**
 * メイン画面の中心となる「舞台」(基本設計書10.2節。メイン画面ワイヤーフレームに準拠)。
 * 背景は透過で、呼び出し側([modifier]の外)が拠点の背景を敷く。
 * - なでる: キャラクター本体をタップする
 * - 餌やり: リュックを開き、ごはんをキャラクターへドラッグ&ドロップする(離した位置がキャラクターに重なれば給餌成立。外れたら元の位置へ戻る。10.2.1節)
 * - 掃除: リュックのそうじ道具をタップして掃除モードに入り、背景の汚れをなでる(5 回で消えて、1 箇所ごとに清潔度 +20。10.2.2節)
 * どちらもアプリ限定の操作で、ウィジェットは単純タップのボタンのまま。
 * 下部には[statusBar]([呼び出し側が満腹度・清潔度・機嫌のゲージを渡す)とリュック(FAB)を横並びに配置する。
 *
 * @param friendVisiting 遊びに来ているフレンドがいれば、その表示文言(いなければ null。9節・10.2節)
 * @param onStainCleaned 汚れ 1 箇所が消えたときに呼ぶ。清潔度を上げ、上がったあとの清潔度を返す
 * @param statusBar 満腹度・清潔度・機嫌のゲージ(呼び出し側が[SegmentedGauge]等で組み立てる)
 * @param characterContent キャラクターの描画。[scale] はスプライトの整数倍率、[widthPx] は舞台の幅(画面外から戻ってくる演出用)
 */
@Composable
fun CareStage(
    isEgg: Boolean,
    riceCount: Int,
    cleanliness: Double,
    field: StainField,
    onFeed: () -> Unit,
    onPet: () -> Unit,
    onStainCleaned: () -> Double,
    modifier: Modifier = Modifier,
    friendVisiting: String? = null,
    statusBar: @Composable () -> Unit = {},
    characterContent: @Composable (scale: Int, widthPx: Float) -> Unit,
) {
    var cleaning by remember { mutableStateOf(false) }
    var packOpen by remember { mutableStateOf(false) }
    var fieldTick by remember { mutableIntStateOf(0) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var characterBounds by remember { mutableStateOf<Rect?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val onStainCleanedNow by rememberUpdatedState(onStainCleaned)
    val onFeedNow by rememberUpdatedState(onFeed)
    val onPetNow by rememberUpdatedState(onPet)

    // 清潔度に応じて汚れが増える(清潔度が下がると新しい汚れが現れる)。卵には汚れはない
    LaunchedEffect(cleanliness, isEgg) {
        if (isEgg) field.sync(0) else field.syncToCleanliness(cleanliness)
        fieldTick++
        if (isEgg) cleaning = false
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(1_800)
            message = null
        }
    }

    /** 掃除モードを終える。なでた回数はリセットする(残った汚れは残る) */
    fun endCleaning() {
        cleaning = false
        field.resetStrokes()
        fieldTick++
    }

    /** 指が [p] にあるときの処理: 汚れがなでられ、5 回で消える。消えた分だけ清潔度を上げる */
    fun touch(p: Offset) {
        val cleaned = field.touch(p.x, p.y, stageSize.width.toFloat(), stageSize.height.toFloat())
        fieldTick++
        if (cleaned.isEmpty()) return
        repeat(cleaned.size) {
            val newCleanliness = onStainCleanedNow()
            field.syncToCleanliness(newCleanliness)
        }
        fieldTick++
        // すべての汚れを落としたら、掃除モードを自動で終える
        if (field.isClean) {
            endCleaning()
            message = "きれいになった!"
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onSizeChanged { stageSize = it }
            .pointerInput(cleaning) {
                if (cleaning) {
                    detectDragGestures(
                        onDragStart = { touch(it) },
                        onDrag = { change, _ ->
                            change.consume()
                            touch(change.position)
                        },
                        onDragEnd = { field.endStroke() },
                        onDragCancel = { field.endStroke() },
                    )
                }
            }
            .pointerInput(cleaning) {
                if (cleaning) {
                    // 汚れ以外の場所をタップしたら、掃除モードを終える(残った汚れは残り、なでた回数はリセット)
                    detectTapGestures(onTap = {
                        if (!field.hasStainNear(it.x, it.y, stageSize.width.toFloat(), stageSize.height.toFloat())) endCleaning()
                    })
                }
            },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val scale = SpriteTimeline.integerScale(with(density) { (maxWidth * 0.45f).roundToPx() }, SPRITE_SOURCE_SIZE)

        // 背景の汚れ
        Canvas(Modifier.matchParentSize()) {
            if (fieldTick >= 0) {
                val base = minOf(size.width, size.height) * 0.05f
                for (s in field.stains) {
                    val c = Offset(s.x * size.width, s.y * size.height)
                    val alpha = 1f - 0.15f * s.strokes // なでるほど薄くなる
                    drawCircle(STAIN_COLOR, base, c, alpha = alpha)
                    drawCircle(STAIN_COLOR, base * 0.7f, c + Offset(base * 0.8f, -base * 0.3f), alpha = alpha)
                    drawCircle(STAIN_COLOR, base * 0.55f, c + Offset(-base * 0.7f, base * 0.35f), alpha = alpha)
                }
            }
        }

        // 遊びに来ているフレンド(9節・10.2節)
        friendVisiting?.let {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .background(Color(0xE6FFFFFF), RoundedCornerShape(50))
                    .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(18.dp).background(PACK_COLOR, CircleShape))
                Text(it, color = Color(0xFF5C4A38), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }

        // キャラクターと案内文(なでる操作の説明)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .onGloballyPositioned { characterBounds = it.boundsInRoot() }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !cleaning && !isEgg,
                    ) {
                        onPetNow()
                        message = "♥"
                    },
            ) {
                characterContent(scale, widthPx)
            }
            if (!isEgg && !cleaning) {
                Text(
                    "キャラクターに直接タッチしてなでられます",
                    Modifier.padding(top = 4.dp),
                    color = Color(0xFF3C2814).copy(alpha = 0.75f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        message?.let {
            Text(
                it,
                Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                color = Color(0xFFE53935),
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
        }
        if (cleaning) {
            Text(
                "汚れをなでて消そう(汚れ以外をタップで終了)",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // どうぐ展開パネル(リュックの真上)
        if (packOpen) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 76.dp)
                    .background(PANEL_BG, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RiceItem(
                    count = riceCount,
                    enabled = !isEgg && !cleaning && riceCount > 0,
                    onDropped = { point ->
                        if (characterBounds?.contains(point) == true) {
                            onFeedNow()
                            message = "♥"
                        }
                    },
                )
                PouchTool(
                    emoji = "🧹",
                    label = "長押しでなぞる",
                    selected = cleaning,
                    enabled = !isEgg,
                    onClick = { if (cleaning) endCleaning() else { cleaning = true; packOpen = false } },
                )
            }
            if (riceCount <= 0) {
                Text(
                    "ごはんがありません(ミニゲームのクリア報酬や探索で手に入ります)",
                    Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp, end = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // 下部:ステータスバーとリュック(FAB)
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) { statusBar() }
            Box(
                Modifier
                    .size(52.dp)
                    .background(PACK_COLOR, CircleShape)
                    .clickable { packOpen = !packOpen },
                contentAlignment = Alignment.Center,
            ) {
                Text("🎒", fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun PouchTool(emoji: String, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(54.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                .background(if (selected) Color(0xFFDBE7E1) else Color(0xFFEFE9DC), RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 24.sp)
        }
        Text(label, color = Color(0xFF7A6350), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

/** 満腹度・清潔度・機嫌などのゲージ表示(基本設計書4.7節・10.2節: 10段階のドットで表示)。 */
@Composable
fun SegmentedGauge(emoji: String, value: Double, filledColor: Color, segments: Int = 10) {
    val filled = (value / 100.0 * segments).toInt().coerceIn(0, segments)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(emoji, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
            repeat(segments) { i ->
                Box(
                    Modifier
                        .size(width = 5.dp, height = 10.dp)
                        .background(if (i < filled) filledColor else Color(0xFFE5DBC8), RoundedCornerShape(1.5.dp)),
                )
            }
        }
    }
}

/** メイン画面下部のステータスバー(満腹度・清潔度・機嫌)の入れ物。角丸の白いピル。 */
@Composable
fun StatusBar(satiety: Double, cleanliness: Double, mood: Double, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xE6FFFFFF), RoundedCornerShape(50))
            .padding(PaddingValues(horizontal = 14.dp, vertical = 8.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentedGauge("🍖", satiety, Color(0xFFE8873A))
        SegmentedGauge("💧", cleanliness, Color(0xFF5B8A7D))
        SegmentedGauge("💗", mood, Color(0xFFC9636B))
    }
}

/** ごはん。長押しして動かし、キャラクターの上で離すと餌をあげる。外れたときは元の位置へ戻る(10.2.1節)。 */
@Composable
private fun RiceItem(count: Int, enabled: Boolean, onDropped: (Offset) -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val onDroppedNow by rememberUpdatedState(onDropped)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(54.dp)
                .zIndex(1f)
                .graphicsLayer {
                    translationX = offset.value.x
                    translationY = offset.value.y
                    scaleX = if (dragging) 1.25f else 1f
                    scaleY = if (dragging) 1.25f else 1f
                    alpha = if (enabled) 1f else 0.4f
                }
                .onGloballyPositioned { if (!dragging) origin = it.boundsInRoot().center }
                .background(Color(0xFFF2DDB8), RoundedCornerShape(14.dp))
                .pointerInput(enabled) {
                    if (enabled) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDrag = { change, delta ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + delta) }
                            },
                            onDragEnd = {
                                onDroppedNow(origin + offset.value)
                                dragging = false
                                scope.launch { offset.animateTo(Offset.Zero, spring()) } // 元の位置へ戻る
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch { offset.animateTo(Offset.Zero, spring()) }
                            },
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("🍙", fontSize = 24.sp)
            Text("×$count", Modifier.align(Alignment.BottomEnd), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Text("ドラッグして与える", color = TEXT_DARK.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}
