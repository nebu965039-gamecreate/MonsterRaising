package com.nebu965039.monsterraising.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.sprite.SpriteFrame
import com.nebu965039.monsterraising.core.sprite.SpriteTimeline
import com.nebu965039.monsterraising.ui.sprite.LoadedSprite
import com.nebu965039.monsterraising.ui.sprite.SpriteAssets
import com.nebu965039.monsterraising.ui.sprite.SpritePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Phase 1 の動作確認用画面。アニメーションを切り替えて差分画像プレイヤーの動きを確認する。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoScreen(characterId: String = "fox") {
    val context = LocalContext.current
    val sprite by produceState<LoadedSprite?>(null, characterId) {
        value = withContext(Dispatchers.IO) { SpriteAssets.load(context, characterId) }
    }
    var selected by remember { mutableStateOf("idle") }
    var current by remember { mutableStateOf<SpriteFrame?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val loaded = sprite
        if (loaded == null) {
            Text("読み込み中…")
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            // 画面幅の 80% に収まる最大の整数倍(48px の 5倍=240px 等)
            val availablePx = with(LocalDensity.current) { (maxWidth * 0.8f).roundToPx() }
            val scale = SpriteTimeline.integerScale(availablePx, loaded.definition.size)
            Box(Modifier.background(Color(0xFFC8DCC8))) {
                SpritePlayer(loaded, selected, scale, onFrameChanged = { current = it })
            }
        }
        Text(
            "選択: $selected / 再生中: ${current?.animation ?: "-"} (${current?.frameKey ?: "-"})",
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            loaded.definition.animations.keys.forEach { name ->
                FilterChip(selected = name == selected, onClick = { selected = name }, label = { Text(name) })
            }
        }
    }
}
