package com.nebu965039.monsterraising.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * メイン画面のヘッダー(基本設計書10.2節)。左にワールドマップへの導線(地図アイコン)、中央に拠点名+天気アイコン、
 * 右にハンバーガーメニュー(フレンド・設定。図鑑への入り口もここに置く)。個別のアイコンは置かず、メニューの中に格納する。
 * 拠点名と天気は、背景・天候システム(10.3節)ができるまでの仮の表示。
 */
@Composable
fun HomeHeader(
    onOpenMap: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenDex: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpenMap) { Text("🗺️", fontSize = 26.sp) }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("森", style = MaterialTheme.typography.titleMedium)
            Text("  ☀", fontSize = 20.sp)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Text("☰", fontSize = 26.sp) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("フレンド") }, onClick = { menuOpen = false; onOpenFriends() })
                DropdownMenuItem(text = { Text("図鑑") }, onClick = { menuOpen = false; onOpenDex() })
                DropdownMenuItem(text = { Text("設定") }, onClick = { menuOpen = false; onOpenSettings() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("開発用") }, onClick = { menuOpen = false; onOpenDev() })
            }
        }
    }
}

/** メイン画面以外の画面の上部: 戻るボタンと画面名。 */
@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Text("←", fontSize = 24.sp) }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
