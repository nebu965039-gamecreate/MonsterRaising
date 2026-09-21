package com.nebu965039.monsterraising.ui.sprite

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nebu965039.monsterraising.core.sprite.SpriteFrame
import com.nebu965039.monsterraising.core.sprite.SpriteTimeline

/**
 * 差分画像プレイヤー。[animation] が変わると先頭から再生し直す。
 * [scale] 倍の整数倍・最近傍(補間なし)で描画する(基本設計書3.3)。
 */
@Composable
fun SpritePlayer(
    sprite: LoadedSprite,
    animation: String,
    scale: Int,
    modifier: Modifier = Modifier,
    onFrameChanged: (SpriteFrame) -> Unit = {},
) {
    var elapsedMs by remember(sprite, animation) { mutableLongStateOf(0L) }
    LaunchedEffect(sprite, animation) {
        var start = -1L
        while (true) {
            withFrameMillis { now ->
                if (start < 0) start = now
                elapsedMs = now - start
            }
        }
    }
    // フレームが切り替わったときだけ再コンポーズされるよう derivedStateOf で絞る
    val frame by remember(sprite, animation) {
        derivedStateOf { SpriteTimeline.resolve(sprite.definition, animation, elapsedMs) }
    }
    LaunchedEffect(frame) { onFrameChanged(frame) }

    val px = sprite.definition.size * scale
    val sizeDp = with(LocalDensity.current) { px.toDp() }
    val image = sprite.frames.getValue(frame.frameKey)
    Canvas(modifier.size(sizeDp)) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(frame.dx * scale, frame.dy * scale),
            dstSize = IntSize(px, px),
            filterQuality = FilterQuality.None,
        )
    }
}
