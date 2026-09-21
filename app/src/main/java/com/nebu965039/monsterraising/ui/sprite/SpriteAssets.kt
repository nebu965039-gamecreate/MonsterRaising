package com.nebu965039.monsterraising.ui.sprite

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nebu965039.monsterraising.core.sprite.SpriteDefinition
import com.nebu965039.monsterraising.core.sprite.SpriteDefinitionParser

/** 定義JSONと、デコード済みの全フレーム画像。 */
class LoadedSprite(val definition: SpriteDefinition, val frames: Map<String, ImageBitmap>)

object SpriteAssets {
    /** `assets/characters/<id>/<id>.json` と、そこから参照される画像を読み込む(IO スレッドで呼ぶこと)。 */
    fun load(context: Context, characterId: String): LoadedSprite {
        val dir = "characters/$characterId"
        val text = context.assets.open("$dir/$characterId.json").bufferedReader().use { it.readText() }
        val def = SpriteDefinitionParser.parse(text)
        // ドット絵を滲ませないため、密度スケーリングを無効にしてデコードする
        val options = BitmapFactory.Options().apply { inScaled = false }
        val frames = def.frames.mapValues { (_, path) ->
            context.assets.open("$dir/$path").use { BitmapFactory.decodeStream(it, null, options)!!.asImageBitmap() }
        }
        return LoadedSprite(def, frames)
    }
}
