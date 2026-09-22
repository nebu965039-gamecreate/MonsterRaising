package com.nebu965039.monsterraising.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ICON_BUTTON_BG = Color(0xD9FFFFFF)
private val ICON_STROKE = Color(0xFF3A2A1C)
private val PILL_BG = Color(0x47000000)

/**
 * メイン画面のヘッダー(基本設計書10.2節。メイン画面ワイヤーフレームに準拠)。
 * 左にワールドマップへの導線(地図アイコン)、中央に拠点名+天気アイコンのピル、右にハンバーガーメニュー
 * (持ち物・フレンド・設定。図鑑への入り口もここに置く)。拠点名と天気は、背景・天候システム(10.3節)ができるまでの仮の表示。
 */
@Composable
fun HomeHeader(
    locationName: String,
    weatherIcon: String,
    onOpenMap: () -> Unit,
    onOpenItems: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenDex: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDev: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(onClick = onOpenMap, contentDescription = "ワールドマップを開く") { MapGlyph() }

        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.background(PILL_BG, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(locationName, color = Color.White, fontSize = 12.sp, style = MaterialTheme.typography.labelLarge)
            }
            Box(Modifier.padding(start = 6.dp).background(PILL_BG, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                Text(weatherIcon, fontSize = 15.sp)
            }
        }

        Box {
            RoundIconButton(onClick = { menuOpen = true }, contentDescription = "メニュー") { MenuGlyph() }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("持ち物") }, onClick = { menuOpen = false; onOpenItems() })
                DropdownMenuItem(text = { Text("フレンド") }, onClick = { menuOpen = false; onOpenFriends() })
                DropdownMenuItem(text = { Text("図鑑") }, onClick = { menuOpen = false; onOpenDex() })
                DropdownMenuItem(text = { Text("設定") }, onClick = { menuOpen = false; onOpenSettings() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text("開発用") }, onClick = { menuOpen = false; onOpenDev() })
            }
        }
    }
}

/** 白い丸背景のアイコンボタン(ワイヤーフレームの地図・メニューボタンに準拠)。 */
@Composable
private fun RoundIconButton(onClick: () -> Unit, contentDescription: String, glyph: @Composable () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .background(ICON_BUTTON_BG, CircleShape)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}

/** 地図(折り畳み地図)のアイコン。 */
@Composable
private fun MapGlyph() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val outline = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.02f, h * 0.22f)
            lineTo(w * 0.35f, h * 0.05f)
            lineTo(w * 0.65f, h * 0.22f)
            lineTo(w * 0.98f, h * 0.05f)
            lineTo(w * 0.98f, h * 0.78f)
            lineTo(w * 0.65f, h * 0.95f)
            lineTo(w * 0.35f, h * 0.78f)
            lineTo(w * 0.02f, h * 0.95f)
            close()
        }
        drawPath(outline, ICON_STROKE, style = stroke)
        drawLine(ICON_STROKE, Offset(w * 0.35f, h * 0.05f), Offset(w * 0.35f, h * 0.78f), strokeWidth = 1.6.dp.toPx())
        drawLine(ICON_STROKE, Offset(w * 0.65f, h * 0.22f), Offset(w * 0.65f, h * 0.95f), strokeWidth = 1.6.dp.toPx())
    }
}

/** ハンバーガーメニューのアイコン(3本線)。 */
@Composable
private fun MenuGlyph() {
    Canvas(Modifier.size(17.dp)) {
        val w = size.width
        val stroke = 2.dp.toPx()
        val rows = listOf(size.height * 0.12f, size.height * 0.5f, size.height * 0.88f)
        for (y in rows) {
            drawLine(ICON_STROKE, Offset(0f, y), Offset(w, y), strokeWidth = stroke, cap = StrokeCap.Round)
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
