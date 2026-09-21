package com.nebu965039.monsterraising.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.scale
import com.nebu965039.monsterraising.MainActivity
import com.nebu965039.monsterraising.R
import com.nebu965039.monsterraising.core.exploration.Exploration
import com.nebu965039.monsterraising.core.minigame.ItemType
import com.nebu965039.monsterraising.core.pet.PetConfig
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.core.sprite.SpriteTimeline
import com.nebu965039.monsterraising.core.widget.PetWidgetModel
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.data.careConfig
import com.nebu965039.monsterraising.ui.common.label

/**
 * ウィジェットの表示を組み立てて反映する。
 * ウィジェットはアニメーションできないため、状態に応じた静止画 1 枚を、整数倍・補間なしで表示する(3.1節・3.3節)。
 */
object PetWidgetUpdater {
    const val ACTION_FEED = "com.nebu965039.monsterraising.widget.FEED"
    const val ACTION_CLEAN = "com.nebu965039.monsterraising.widget.CLEAN"
    const val ACTION_PET = "com.nebu965039.monsterraising.widget.PET"

    private const val CHARACTER = "fox"
    private const val SOURCE_SIZE = 48
    private const val DEFAULT_IMAGE_DP = 96

    /** 経過時間ぶんの状態を反映して保存し、すべてのウィジェットを更新する。 */
    fun refreshAll(context: Context) {
        val config = careConfig(context)
        PetStore(context).update(System.currentTimeMillis(), config) {
            PetSimulator.advance(it, System.currentTimeMillis(), config)
        }
        updateAll(context)
    }

    /** 保存されている状態をそのまま表示に反映する(状態は変えない)。 */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PetWidgetProvider::class.java))
        for (id in ids) update(context, manager, id)
    }

    fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val config = careConfig(context)
        val state = PetStore(context).update(System.currentTimeMillis(), config) { it }
        // 探索中・探索完了は、ウィジェットにも表示する(キャラクターは消えない。8.6節)
        val progress = MiniGameStore(context).load()
        val exploration = Exploration.summaryLine(Exploration.overview(progress, System.currentTimeMillis()))
        val model = PetWidgetModel.of(state, config, exploration, riceCount = progress.inventory.count(ItemType.RICE))
        val views = RemoteViews(context.packageName, R.layout.widget_pet)

        val scale = imageScale(context, manager.getAppWidgetOptions(appWidgetId))
        views.setImageViewBitmap(R.id.widget_image, frameBitmap(context, model.frameKey, scale))
        views.setTextViewText(R.id.widget_title, "${model.generation}代目 ${model.stage.label()}")
        if (model.explorationNote != null) {
            views.setTextViewText(R.id.widget_exploration, model.explorationNote)
            views.setViewVisibility(R.id.widget_exploration, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_exploration, View.GONE)
        }
        if (model.isEgg) {
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_egg_note))
            views.setViewVisibility(R.id.widget_buttons, View.GONE)
        } else {
            views.setTextViewText(
                R.id.widget_status,
                listOf(
                    context.getString(R.string.gauge_satiety) to model.satietyDots,
                    context.getString(R.string.gauge_cleanliness) to model.cleanlinessDots,
                    context.getString(R.string.gauge_mood) to model.moodDots,
                ).joinToString("\n") { (label, dots) -> "$label ${dotBar(dots ?: 0)}" },
            )
            views.setViewVisibility(R.id.widget_buttons, View.VISIBLE)
        }

        // ウィジェットをタップすると本体アプリへ(ロードマップ Phase 3)
        views.setOnClickPendingIntent(
            R.id.widget_image,
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        // 餌のボタンには、ごはんの残りを表示する(なければ押せない)
        views.setTextViewText(R.id.widget_feed, context.getString(R.string.widget_feed_count, model.riceCount))
        views.setBoolean(R.id.widget_feed, "setEnabled", model.canFeed)
        // 単発タップの簡易お世話(ドラッグ&ドロップ等はウィジェットでは扱えないため本体アプリ限定。10.2.1節)
        views.setOnClickPendingIntent(R.id.widget_feed, actionIntent(context, ACTION_FEED, 1))
        views.setOnClickPendingIntent(R.id.widget_clean, actionIntent(context, ACTION_CLEAN, 2))
        views.setOnClickPendingIntent(R.id.widget_pet, actionIntent(context, ACTION_PET, 3))
        manager.updateAppWidget(appWidgetId, views)
    }

    /** 餌・掃除・なでるの簡易アクションを保存状態へ反映してから、表示を更新する。 */
    fun handleAction(context: Context, action: String) {
        val now = System.currentTimeMillis()
        // 餌やりは、ごはんを 1 個消費する。卵・ごはんなしのときは何もしない(表示だけ更新する)
        if (action == ACTION_FEED) {
            val isEgg = PetStore(context).load()?.stage == Stage.EGG
            val consumed = !isEgg && MiniGameStore(context).update { p ->
                val inventory = p.inventory.consume(ItemType.RICE)
                if (inventory == null) p to false else p.copy(inventory = inventory) to true
            }
            if (!consumed) {
                updateAll(context)
                return
            }
        }
        val config = careConfig(context)
        PetStore(context).update(now, config) { state ->
            when (action) {
                ACTION_FEED -> PetSimulator.feed(state, now, config.feedGain, config)
                // 掃除はウィジェットでは簡易版(汚れ 1 箇所ぶん。4.2節)
                ACTION_CLEAN -> PetSimulator.clean(state, now, config.cleanGainPerStain, config)
                ACTION_PET -> PetSimulator.pet(state, now, config)
                else -> state
            }
        }
        updateAll(context)
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, PetWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun dotBar(dots: Int): String =
        "●".repeat(dots) + "○".repeat(PetWidgetModel.DOT_MAX - dots)

    /** ウィジェットの大きさに収まる、最大の整数倍(最低 1 倍)。 */
    private fun imageScale(context: Context, options: android.os.Bundle): Int {
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_IMAGE_DP)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_IMAGE_DP)
        // 画像に使えるのは、幅の 6 割・高さの 4 割程度(残りはステータスとボタン)
        val dp = minOf(minWidthDp * 0.6f, minHeightDp * 0.4f)
        val px = (dp * context.resources.displayMetrics.density).toInt()
        return SpriteTimeline.integerScale(px, SOURCE_SIZE)
    }

    private val bitmapCache = object : LruCache<String, Bitmap>(8) {}

    /** アセットのフレーム画像を整数倍・補間なし(最近傍)に拡大して返す。 */
    private fun frameBitmap(context: Context, frameKey: String, scale: Int): Bitmap {
        val key = "$frameKey@$scale"
        bitmapCache.get(key)?.let { return it }
        val options = BitmapFactory.Options().apply { inScaled = false }
        val src = context.assets.open("characters/$CHARACTER/frames/$frameKey.png").use {
            BitmapFactory.decodeStream(it, null, options)!!
        }
        val scaled = src.scale(src.width * scale, src.height * scale, filter = false)
        bitmapCache.put(key, scaled)
        return scaled
    }
}
