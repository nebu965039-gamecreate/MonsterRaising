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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.nebu965039.monsterraising.core.care.StainRules
import com.nebu965039.monsterraising.core.sprite.SpriteTimeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPRITE_SOURCE_SIZE = 48
private val STAGE_COLOR = Color(0xFFC8DCC8)
private val STAIN_COLOR = Color(0xFF6B4A2B)

/**
 * メイン画面のお世話操作(基本設計書10.2節)。キャラクターを中心に、背景の汚れ・リュックを表示する。
 * - なでる: キャラクター本体をタップする
 * - 餌やり: リュックを開き、ごはんをキャラクターへドラッグ&ドロップする(離した位置がキャラクターに重なれば給餌成立。外れたら元の位置へ戻る。10.2.1節)
 * - 掃除: リュックのそうじ道具をタップして掃除モードに入り、背景の汚れをなでる(5 回で消えて、1 箇所ごとに清潔度 +20。10.2.2節)
 * どちらもアプリ限定の操作で、ウィジェットは単純タップのボタンのまま。
 *
 * @param onStainCleaned 汚れ 1 箇所が消えたときに呼ぶ。清潔度を上げ、上がったあとの清潔度を返す
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

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
                .background(STAGE_COLOR)
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
            val scale = SpriteTimeline.integerScale(with(density) { (maxWidth * 0.5f).roundToPx() }, SPRITE_SOURCE_SIZE)

            // 背景の汚れ
            Canvas(Modifier.matchParentSize()) {
                if (fieldTick >= 0) {
                    val base = minOf(size.width, size.height) * 0.07f
                    for (s in field.stains) {
                        val c = Offset(s.x * size.width, s.y * size.height)
                        val alpha = 1f - 0.15f * s.strokes // なでるほど薄くなる
                        drawCircle(STAIN_COLOR, base, c, alpha = alpha)
                        drawCircle(STAIN_COLOR, base * 0.7f, c + Offset(base * 0.8f, -base * 0.3f), alpha = alpha)
                        drawCircle(STAIN_COLOR, base * 0.55f, c + Offset(-base * 0.7f, base * 0.35f), alpha = alpha)
                    }
                }
            }

            // キャラクター(タップでなでる)
            Box(
                Modifier
                    .align(Alignment.Center)
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

            message?.let {
                Text(
                    it,
                    Modifier.align(Alignment.TopCenter),
                    color = Color(0xFFE53935),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                )
            }
            if (cleaning) {
                Text(
                    "汚れをなでて消そう(汚れ以外をタップで終了)",
                    Modifier.align(Alignment.BottomCenter),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // リュック(ごはんと、そうじ道具)
        OutlinedButton(onClick = { packOpen = !packOpen }) { Text(if (packOpen) "リュックを閉じる" else "🎒 リュックを開く") }
        if (packOpen) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                FilterChip(
                    selected = cleaning,
                    enabled = !isEgg,
                    onClick = { if (cleaning) endCleaning() else cleaning = true },
                    label = { Text("🧹 そうじ道具") },
                )
            }
            Text(
                if (riceCount > 0) "ごはんをキャラクターへドラッグして餌をあげよう" else "ごはんがありません(ミニゲームのクリア報酬や探索で手に入ります)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
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

    Box(
        Modifier
            .size(64.dp)
            .zIndex(1f)
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                scaleX = if (dragging) 1.25f else 1f
                scaleY = if (dragging) 1.25f else 1f
                alpha = if (enabled) 1f else 0.4f
            }
            .onGloballyPositioned { if (!dragging) origin = it.boundsInRoot().center }
            .background(Color(0x22000000), RoundedCornerShape(16.dp))
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
        Text("🍙", fontSize = 32.sp)
        Text("×$count", Modifier.align(Alignment.BottomEnd), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}
