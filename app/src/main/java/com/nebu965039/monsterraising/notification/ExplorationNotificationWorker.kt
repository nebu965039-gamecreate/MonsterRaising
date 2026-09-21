package com.nebu965039.monsterraising.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nebu965039.monsterraising.core.exploration.ExplorationSite
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.widget.PetWidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * 探索が終わる時刻に、完了の通知を出し、ウィジェットの表示を更新する(基本設計書8.6節)。
 * 探索を始めたときに、所要時間ぶんの遅延で予約する。予約は端末の再起動後も残る。
 * 通知の前に、その探索がまだ受け取り待ちとして残っているか(同じ開始時刻の探索か)を確かめる。
 */
class ExplorationNotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val site = inputData.getString(KEY_SITE)?.let { runCatching { ExplorationSite.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        val startedAtMs = inputData.getLong(KEY_STARTED_AT, -1L)
        val stillWaiting = MiniGameStore(applicationContext).load().exploration.actives
            .any { it.site == site && it.startedAtMs == startedAtMs }
        if (stillWaiting) {
            ExplorationNotifier.notifyFinished(applicationContext, site)
            PetWidgetUpdater.updateAll(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val KEY_SITE = "site"
        private const val KEY_STARTED_AT = "startedAtMs"

        /** [delayMs] 後に、[site] の探索の完了を知らせる。同じ拠点の古い予約は置き換える。 */
        fun schedule(context: Context, site: ExplorationSite, startedAtMs: Long, delayMs: Long) {
            val request = OneTimeWorkRequest.Builder(ExplorationNotificationWorker::class.java)
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(KEY_SITE, site.name).putLong(KEY_STARTED_AT, startedAtMs).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("exploration_done_${site.name}", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
