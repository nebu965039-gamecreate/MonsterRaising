package com.nebu965039.monsterraising.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nebu965039.monsterraising.MainActivity
import com.nebu965039.monsterraising.R
import com.nebu965039.monsterraising.core.exploration.ExplorationSite

/** 探索が完了したときの通知(基本設計書8.6節)。 */
object ExplorationNotifier {
    private const val CHANNEL_ID = "exploration"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_exploration), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** 拠点ごとに通知を出す(通知の ID は拠点ごとに別なので、複数の拠点が同時に終わっても並ぶ)。 */
    fun notifyFinished(context: Context, site: ExplorationSite) {
        // Android 13 以降は、通知の許可がなければ出さない(lint が読み取れるよう、呼び出しの直前で確認する)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_exploration_title))
            .setContentText(context.getString(R.string.notification_exploration_text, site.displayName))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + site.ordinal, notification)
    }

    private const val NOTIFICATION_ID_BASE = 1_000
}
