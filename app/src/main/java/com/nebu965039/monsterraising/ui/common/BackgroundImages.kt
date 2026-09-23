package com.nebu965039.monsterraising.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.IOException

/** ゲームセンターの背景(ミニゲーム3本共通。8.1節: ゲーム拠点はミニゲーム1本につき1つの建物) */
const val GAME_CENTER_BACKGROUND_PATH = "backgrounds/gamecenter/gamecenter.png"

/** `assets/<path>` の画像を読み込む共通ヘルパー(拠点・ゲームセンター・探索拠点の背景で共通)。 */
object BackgroundImages {
    fun load(context: Context, path: String): ImageBitmap? = try {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (_: IOException) {
        null
    }
}

/**
 * 画面いっぱいに背景画像を敷いてから [content] を描く(設計書10.3.0節: ドット絵・整数倍相当のニアレストネイバー表示)。
 * [image] が未読み込み・未配置(null)なら、[fallback] があればそれを敷く(仮表示への安全なフォールバック)。
 * [scrimAlpha] > 0 なら、白の被膜を重ねて画像を落ち着かせ、上に乗せる文字の可読性を確保する
 * (お世話の舞台のように文字側の色を個別に調整していない画面向け)。
 */
@Composable
fun ScreenBackground(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0f,
    fallback: @Composable (BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        if (image != null) {
            Image(
                image,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
            )
            if (scrimAlpha > 0f) {
                Box(Modifier.matchParentSize().background(Color.White.copy(alpha = scrimAlpha)))
            }
        } else {
            fallback?.invoke(this)
        }
        content()
    }
}
