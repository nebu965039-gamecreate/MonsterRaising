package com.nebu965039.monsterraising.ui.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
 * フレンドがいるかの切り替え(フレンド機能ができるまでの代用)と、キャラクターのアニメーション確認。
 * 時間を進める操作(仮想時計)などは、メイン画面の「デモ用の操作」にある。
 */
@Composable
fun DevScreen() {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(checked = DemoFriends.hasFriends, onCheckedChange = { DemoFriends.hasFriends = it })
            Text("フレンドがいる(探索の同時実行・協力の表示)", style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.weight(1f)) { DemoScreen() }
    }
}
