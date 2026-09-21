package com.nebu965039.monsterraising.ui.map

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nebu965039.monsterraising.core.exploration.Exploration
import com.nebu965039.monsterraising.core.exploration.ExplorationConfig
import com.nebu965039.monsterraising.core.exploration.ExplorationPlan
import com.nebu965039.monsterraising.core.exploration.ExplorationSite
import com.nebu965039.monsterraising.core.exploration.ExplorationStatus
import com.nebu965039.monsterraising.core.exploration.StartResult
import com.nebu965039.monsterraising.core.minigame.ItemType
import com.nebu965039.monsterraising.core.minigame.MiniGameProgress
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.core.sprite.SpriteTimeline
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.notification.ExplorationNotificationWorker
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.ui.demo.DemoFriends
import com.nebu965039.monsterraising.ui.minigame.itemName
import com.nebu965039.monsterraising.ui.sprite.LoadedSprite
import com.nebu965039.monsterraising.ui.sprite.SpriteAssets
import com.nebu965039.monsterraising.ui.sprite.SpritePlayer
import com.nebu965039.monsterraising.widget.PetWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val config = ExplorationConfig()

/** 拠点の画面での、選択の段階。 */
private enum class SiteStep { INTRO, PLAN }

private fun itemsText(items: Map<ItemType, Int>): String =
    items.entries.sortedBy { it.key.ordinal }.joinToString(" / ") { (item, n) -> "${itemName(item)} ×$n" }

/** 拠点の背景の仮の色(本番の背景画像ができるまで)。 */
private fun ExplorationSite.background(): Brush = when (this) {
    ExplorationSite.CAVE -> Brush.verticalGradient(listOf(Color(0xFF1B1B26), Color(0xFF3B3A4A)))
    ExplorationSite.COAST -> Brush.verticalGradient(listOf(Color(0xFF8ED1F0), Color(0xFFF2E3B3)))
    ExplorationSite.MOUNTAIN -> Brush.verticalGradient(listOf(Color(0xFF9CC7A4), Color(0xFF3F6B45)))
}

/**
 * 探索拠点(基本設計書8節)の画面。ワールドマップ(10.4節)で拠点を選ぶと、この画面に移る。
 * 「探索をしますか? / `探索する` / `この場を離れる`」→「少し探索(1回)/ じっくり探索(10回)」と選ぶと、
 * キャラクターが背景のほうへ小さくなっていき、探索が始まる。探索中は「探索中(残り〜)」と表示する。
 * 同時に探索できるのは 1 か所で、フレンドがいれば 3 か所まで。2 か所目以降は「フレンドが協力しに来てくれました」と表示する(8.6節)。
 * `この場を離れる` を選ぶと、ワールドマップに戻る([onLeave])。
 */
@Composable
fun ExplorationSiteScreen(site: ExplorationSite, onLeave: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MiniGameStore(context) }
    val petStore = remember { PetStore(context) }
    var progress by remember { mutableStateOf(store.load()) }
    var nowMs by remember { mutableLongStateOf(DemoClock.now()) }

    // Android 13 以降は、通知の許可を求める(探索の完了を通知するため)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 残り時間の表示を更新する
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = DemoClock.now()
            delay(1_000)
        }
    }
    // ログインボーナスは、アプリを開いたときに受け取る(AppRoot)。ほかの画面で増えた持ち物を読み直す
    LaunchedEffect(Unit) {
        while (true) {
            progress = store.load()
            delay(2_000)
        }
    }

    SiteView(
        site = site,
        progress = progress,
        nowMs = nowMs,
        isEgg = petStore.load()?.stage == Stage.EGG,
        onStart = { plan ->
            requestNotificationPermission()
            val started = store.update { p ->
                when (val r = Exploration.start(p, site, plan, DemoClock.now(), hasFriends = DemoFriends.hasFriends)) {
                    is StartResult.Started -> r.progress to r.progress.exploration.actives.first { it.site == site }
                    else -> p to null
                }
            }
            progress = store.load()
            if (started != null) {
                // 終わる時刻に、完了の通知を出す(予約は端末の再起動後も残る)
                ExplorationNotificationWorker.schedule(context, site, started.startedAtMs, started.endsAtMs - started.startedAtMs)
                PetWidgetUpdater.updateAll(context)
            }
            started != null
        },
        onCollect = {
            val result = store.update { p ->
                val c = Exploration.collect(p, site, DemoClock.now())
                (c?.progress ?: p) to c?.rewards
            }
            progress = store.load()
            PetWidgetUpdater.updateAll(context)
            result
        },
        onLeave = onLeave,
    )
}

@Composable
private fun SiteView(
    site: ExplorationSite,
    progress: MiniGameProgress,
    nowMs: Long,
    isEgg: Boolean,
    onStart: (ExplorationPlan) -> Boolean,
    onCollect: () -> Map<ItemType, Int>?,
    onLeave: () -> Unit,
) {
    val context = LocalContext.current
    val sprite by produceState<LoadedSprite?>(null) {
        value = withContext(Dispatchers.IO) { SpriteAssets.load(context, "fox") }
    }
    var step by remember(site) { mutableStateOf(SiteStep.INTRO) }
    var departing by remember(site) { mutableStateOf(false) }
    var collected by remember(site) { mutableStateOf<Map<ItemType, Int>?>(null) }
    val departure = remember(site) { Animatable(0f) }

    // 出発の演出: キャラクターが背景のほうへ向かって小さくなっていく
    LaunchedEffect(departing) {
        if (departing) {
            departure.snapTo(0f)
            departure.animateTo(1f, tween(durationMillis = 1_800))
            departing = false
        }
    }

    val status = Exploration.status(progress, site, nowMs)
    val points = progress.inventory.explorationPoints
    val capacity = config.capacity(DemoFriends.hasFriends)
    val used = progress.exploration.actives.size
    val hasFreeSlot = used < capacity

    Box(Modifier.fillMaxSize().background(site.background())) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(site.displayName, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)

            // キャラクター(この拠点を探索に出ていないとき、または出発の演出中だけ表示する)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                val loaded = sprite
                val away = status is ExplorationStatus.InProgress && !departing
                if (loaded != null && !away) {
                    val availablePx = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.6f).roundToPx() }
                    val scale = SpriteTimeline.integerScale(availablePx, loaded.definition.size)
                    val rise = with(LocalDensity.current) { (maxHeight * 0.55f).toPx() }
                    val p = departure.value
                    Box(
                        Modifier
                            .graphicsLayer {
                                val s = 1f - 0.85f * p
                                scaleX = s
                                scaleY = s
                                translationY = -rise * p
                                // 拡大の基準は足元(下端中央)にして、奥へ遠ざかるように見せる
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .alpha(1f - 0.5f * p),
                    ) {
                        SpritePlayer(loaded, animation = if (departing) "walk" else "idle", scale = scale)
                    }
                }
            }

            // 案内と選択肢
            Column(
                Modifier.fillMaxWidth().background(Color(0xCC101418), RoundedCornerShape(16.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val text = Color.White
                when {
                    departing -> Text("行ってきます!", color = text, style = MaterialTheme.typography.titleMedium)

                    status is ExplorationStatus.InProgress -> {
                        Text("探索中(残り ${Exploration.remainingText(status.remainingMs)})", color = text, style = MaterialTheme.typography.titleMedium)
                        Text("キャラクターは探索に出かけています。終わるまでお待ちください。", color = text, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onLeave) { Text("ワールドマップへ") }
                    }

                    status is ExplorationStatus.Finished -> {
                        Text("探索が終わりました!", color = text, style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { collected = onCollect() }) { Text("成果を受け取る") }
                        OutlinedButton(onClick = onLeave) { Text("ワールドマップへ") }
                    }

                    isEgg -> {
                        Text(site.intro, color = text, style = MaterialTheme.typography.bodyMedium)
                        Text("卵の間は探索に行けません。", color = text, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onLeave) { Text("この場を離れる") }
                    }

                    step == SiteStep.INTRO -> {
                        collected?.let {
                            Text("手に入れたもの: ${itemsText(it)}", color = Color(0xFFFFE082), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(site.intro, color = text, style = MaterialTheme.typography.bodyMedium)
                        Text("探索をしますか?", color = text, style = MaterialTheme.typography.titleMedium)
                        Button(enabled = hasFreeSlot, onClick = { step = SiteStep.PLAN; collected = null }) { Text("探索する") }
                        if (!hasFreeSlot) {
                            Text(
                                if (DemoFriends.hasFriends) {
                                    "同時に探索できる 3 か所を使い切っています。"
                                } else {
                                    "ほかの拠点を探索中のため、いまは探索できません。フレンドがいると、最大 3 か所まで同時に探索できます。"
                                },
                                color = text,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(onClick = onLeave) { Text("この場を離れる") }
                    }

                    else -> {
                        // 2 か所目以降は、フレンドの協力による
                        if (Exploration.isFriendHelp(progress)) {
                            Text("フレンドが協力しに来てくれました!", color = Color(0xFFFFE082), style = MaterialTheme.typography.titleMedium)
                        }
                        Text("どのくらい探索しますか?(所持ポイント $points)", color = text, style = MaterialTheme.typography.titleMedium)
                        ExplorationPlan.entries.forEach { plan ->
                            val cost = config.cost(plan)
                            Button(
                                enabled = points >= cost,
                                onClick = { if (onStart(plan)) departing = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${plan.label}(${plan.runs}回)", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "消費 $cost ポイント・${Exploration.remainingText(config.duration(plan))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        if (points < config.cost(ExplorationPlan.SHORT)) {
                            Text("探索ポイントが足りません。ミニゲームのクリア報酬や、毎日のログインで貯まります。", color = text, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { step = SiteStep.INTRO }) { Text("戻る") }
                    }
                }
            }
        }
    }
}
