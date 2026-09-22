package com.nebu965039.monsterraising.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nebu965039.monsterraising.core.exploration.ExplorationSite
import java.io.IOException

/**
 * 探索拠点の背景画像(任意。設計書10.3.0節)。`assets/backgrounds/sites/<拠点の小文字>.png`
 * (`cave.png` `coast.png` `mountain.png`。`mountain.png` は「森」に改名する前の名残のファイル名)
 * を置くと、その拠点の背景として使われる。置かれていない拠点は、色のグラデーションで仮表示する([ExplorationSiteScreen]側)。
 */
object SiteBackgrounds {
    private const val DIR = "backgrounds/sites"

    fun fileName(site: ExplorationSite): String = "${site.name.lowercase()}.png"

    fun load(context: Context, site: ExplorationSite): ImageBitmap? = try {
        context.assets.open("$DIR/${fileName(site)}").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (_: IOException) {
        null
    }
}
