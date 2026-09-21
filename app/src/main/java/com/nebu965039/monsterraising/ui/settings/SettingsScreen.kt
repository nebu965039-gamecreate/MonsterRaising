package com.nebu965039.monsterraising.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.nebu965039.monsterraising.data.settingsStore
import kotlinx.coroutines.delay

/**
 * 設定画面(基本設計書10.1節)の骨組み。
 * - 通知:探索の完了通知(8.6節)のオン/オフと、端末の通知設定への導線
 * - 位置情報:天気を現在地と連動させる(10.3節)ための、許可のやり直しの導線。天気の連動自体は準備中
 * - フレンド:ログイン状況・ステータスをフレンドに表示するか(9節)。サーバー(Phase 7)で使う
 * - アプリ情報
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { settingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    // 端末の設定から戻ってきたときに、許可の状態を読み直す
    LaunchedEffect(Unit) {
        while (true) {
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            delay(1_500)
        }
    }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // 開ける設定画面がない端末では、何もしない
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("通知")
        SwitchRow("探索が終わったときに通知する", settings.notifyExploration) { on ->
            settings = store.update { it.copy(notifyExploration = on) }
        }
        Text(
            "端末の通知の許可: " + if (notificationsAllowed) "許可されています" else "許可されていません(通知は出ません)",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text("端末の通知設定を開く") }
        HorizontalDivider()

        Section("位置情報・天気")
        Text(
            "天気を、実際の天気(現在地の天気)と連動させるには、位置情報の許可が必要です。天気の連動は準備中です。" +
                "許可を出し直したいときは、アプリの設定を開いてください。許可しない場合は、その日の天気を確率で決めます。",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }) { Text("アプリの設定を開く") }
        HorizontalDivider()

        Section("フレンド")
        SwitchRow("ログイン状況・ステータスをフレンドに表示する", settings.showStatusToFriends) { on ->
            settings = store.update { it.copy(showStatusToFriends = on) }
        }
        Text("フレンド機能(準備中)で使う設定です。", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()

        Section("アプリ情報")
        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "不明"
        }
        Text("MonsterRaising  バージョン $version", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Section(title: String) = Text(title, style = MaterialTheme.typography.titleMedium)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
