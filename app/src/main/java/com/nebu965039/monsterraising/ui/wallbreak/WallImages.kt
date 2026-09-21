package com.nebu965039.monsterraising.ui.wallbreak

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nebu965039.monsterraising.minigame.wallbreak.WallColor

/**
 * 壁のドット絵(任意)。`assets/wallbreak/walls/<色名の小文字>.png`(例: `red.png`、`black.png`)を置くと、その色の壁の絵として使われる。
 * 置かれていない色は、色付きの四角で表示する。ドット絵が滲まないよう、密度スケーリングなしで読み込み、補間なしで描画する。
 */
object WallImages {
    private const val DIR = "wallbreak/walls"

    fun fileName(color: WallColor): String = "${color.name.lowercase()}.png"

    fun load(context: Context): Map<WallColor, ImageBitmap> {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val result = mutableMapOf<WallColor, ImageBitmap>()
        for (color in WallColor.entries) {
            val bitmap = try {
                context.assets.open("$DIR/${fileName(color)}").use { BitmapFactory.decodeStream(it, null, options) }
            } catch (_: java.io.IOException) {
                null
            } ?: continue
            result[color] = bitmap.asImageBitmap()
        }
        return result
    }
}
