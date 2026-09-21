package com.nebu965039.monsterraising.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** ホーム画面ウィジェット(RemoteViews)。表示は [PetWidgetUpdater]、定期更新は [PetWidgetWorker]。 */
class PetWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PetWidgetUpdater.refreshAll(context)
        PetWidgetWorker.schedule(context)
    }

    /** 最初のウィジェットが置かれたら定期更新を始め、最後のウィジェットが外されたら止める。 */
    override fun onEnabled(context: Context) = PetWidgetWorker.schedule(context)

    override fun onDisabled(context: Context) = PetWidgetWorker.cancel(context)

    /** サイズ変更時は、新しい大きさに合わせて画像の倍率を取り直す。 */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) = PetWidgetUpdater.update(context, appWidgetManager, appWidgetId)

    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            PetWidgetUpdater.ACTION_FEED, PetWidgetUpdater.ACTION_CLEAN, PetWidgetUpdater.ACTION_PET ->
                PetWidgetUpdater.handleAction(context, action)
            else -> super.onReceive(context, intent)
        }
    }
}
