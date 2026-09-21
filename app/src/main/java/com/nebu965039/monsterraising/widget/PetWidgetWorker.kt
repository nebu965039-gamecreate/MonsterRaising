package com.nebu965039.monsterraising.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** ウィジェットの定期更新。時間経過による減少・進化・死亡を反映して表示を更新する(WorkManager の最短周期は 15 分)。 */
class PetWidgetWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        PetWidgetUpdater.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "pet_widget_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequest.Builder(PetWidgetWorker::class.java, 15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
