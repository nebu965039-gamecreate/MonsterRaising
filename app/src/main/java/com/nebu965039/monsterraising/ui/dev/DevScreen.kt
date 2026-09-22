package com.nebu965039.monsterraising.ui.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.ui.demo.DemoFriends
import com.nebu965039.monsterraising.ui.demo.DemoScreen

/**
 * 開発用の画面(動作確認用)。メイン画面のヘッダーのメニューから開く。
 * - フレンドがいるかの切り替え(フレンド機能ができるまでの代用。探索の同時実行・協力・来訪表示に使う)
 * - 育成ロジックの動作確認([DemoControls]。数値を見ながら餌やり・掃除・なでる・仮想時計を試す)
 * - キャラクターのアニメーション確認([DemoScreen])
 */
@Composable
fun DevScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = DemoFriends.hasFriends, onCheckedChange = { DemoFriends.hasFriends = it })
            Text("フレンドがいる(探索の同時実行・協力・来訪の表示)", style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider()
        DemoControls()
        HorizontalDivider()
        Text("アニメーション確認", style = MaterialTheme.typography.titleMedium)
        // DemoScreen は内部で fillMaxSize を使うため、スクロール可能な親の中では高さを区切って渡す
        Box(Modifier.fillMaxWidth().height(420.dp)) { DemoScreen() }
    }
}
