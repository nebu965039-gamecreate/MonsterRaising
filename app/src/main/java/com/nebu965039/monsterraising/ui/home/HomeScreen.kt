package com.nebu965039.monsterraising.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.care.CareSession
import com.nebu965039.monsterraising.core.exploration.Exploration
import com.nebu965039.monsterraising.core.minigame.ItemType
import com.nebu965039.monsterraising.core.pet.DeathCause
import com.nebu965039.monsterraising.core.pet.PetAppearance
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.data.careConfig
import com.nebu965039.monsterraising.ui.care.CareStage
import com.nebu965039.monsterraising.ui.care.StatusBar
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.ui.demo.DemoFriends
import com.nebu965039.monsterraising.ui.nav.HomeHeader
import com.nebu965039.monsterraising.ui.sprite.LoadedSprite
import com.nebu965039.monsterraising.ui.sprite.SpriteAssets
import com.nebu965039.monsterraising.ui.sprite.SpritePlayer
import com.nebu965039.monsterraising.widget.PetWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** メイン画面の仮の拠点名・天気(背景・天候システム 10.3節ができるまでの仮表示)。 */
private const val LOCATION_NAME = "廃屋"
private const val WEATHER_ICON = "☀"

private val BG_GRADIENT = Brush.verticalGradient(listOf(Color(0xFFD8C9B0), Color(0xFFE9DDC4), Color(0xFFF3ECD6)))

/**
 * メイン画面(育成画面。基本設計書10.2節、メイン画面ワイヤーフレームに準拠)。
 * ヘッダー・拠点の背景・お世話の舞台([CareStage])・ステータスバーをまとめる。
 * 時間経過に伴う状態更新・探索から戻る演出・世代交代の通知など、Phase 2 以降のロジックはここで持つ。
 * 見た目は全段階で同じ仮キャラを使う(段階ごとの見た目差分は未作成)。
 */
@Composable
fun HomeScreen(
    characterId: String = "fox",
    resumeTick: Int = 0,
    onOpenMap: () -> Unit = {},
    onOpenItems: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenDex: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDev: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { PetStore(context) }
    // 装着中の装備(満腹度・清潔度の減少速度)を反映した調整値。装備は持ち物画面で変えるので、この画面を開くたびに読み直す
    val config = remember { careConfig(context) }
    val sprite by produceState<LoadedSprite?>(null, characterId) {
        value = withContext(Dispatchers.IO) { SpriteAssets.load(context, characterId) }
    }
    fun now() = DemoClock.now()

    var pet by remember {
        mutableStateOf(store.update(now(), config) { PetSimulator.advance(it, now(), config) })
    }
    // 単発の演出(食事・なでる)。終わって idle に戻ったら基本アニメーションへ戻す
    var oneShot by remember { mutableStateOf<String?>(null) }
    var shotToken by remember { mutableIntStateOf(0) }

    var notice by remember { mutableStateOf<String?>(null) }
    // 最新の保存状態(ウィジェットの操作を含む)を読んで更新し、ウィジェットにも反映する
    fun commit(op: (PetState) -> PetState) {
        val next = store.update(now(), config, op)
        if (next.generation > pet.generation) {
            notice = when (next.lastDeathCause) {
                DeathCause.OLD_AGE -> "寿命を迎えました。卵を残しました(${next.generation}代目。先代の有効度の5%を引き継ぎ)"
                else -> "放置により死亡しました。卵が残っていました(${next.generation}代目)"
            }
        }
        pet = next
        PetWidgetUpdater.updateAll(context)
    }

    // ウィジェットで操作して戻ってきたときなど、アプリが前面に戻るたびに読み直す
    LaunchedEffect(resumeTick) { commit { PetSimulator.advance(it, now(), config) } }
    // 探索中の状況(8.6節)。前面に戻ったとき・5 秒ごとに読み直す
    val progressStore = remember { MiniGameStore(context) }
    var exploreTick by remember { mutableIntStateOf(0) }
    // ごはんの数(餌やりで消費する。10.2.1節)。前面に戻ったときにも読み直す
    var care by remember { mutableStateOf(progressStore.load()) }
    LaunchedEffect(resumeTick) { care = progressStore.load() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            commit { PetSimulator.advance(it, now(), config) }
            exploreTick++
            care = progressStore.load()
        }
    }
    val overview = remember(resumeTick, exploreTick, DemoClock.offsetMs) { Exploration.overview(progressStore.load(), now()) }
    val exploring = overview.inProgress.isNotEmpty()
    // 探索中にこの画面を表示すると、しばらくしてから画面の外(右)から歩いて戻ってくる。0 = 定位置、1 = 画面の外
    val comeBack = remember { Animatable(0f) }
    LaunchedEffect(resumeTick, exploring) {
        if (exploring) {
            comeBack.snapTo(1f)
            delay(RETURN_DELAY_MS)
            comeBack.animateTo(0f, tween(RETURN_DURATION_MS, easing = LinearEasing))
        } else {
            comeBack.snapTo(0f)
        }
    }

    fun play(name: String) {
        oneShot = name
        shotToken++
    }

    val loaded = sprite
    Box(Modifier.fillMaxSize().background(BG_GRADIENT)) {
        LocationBackdrop()

        Column(Modifier.fillMaxSize()) {
            HomeHeader(
                locationName = LOCATION_NAME,
                weatherIcon = WEATHER_ICON,
                onOpenMap = onOpenMap,
                onOpenItems = onOpenItems,
                onOpenFriends = onOpenFriends,
                onOpenDex = onOpenDex,
                onOpenSettings = onOpenSettings,
                onOpenDev = onOpenDev,
                modifier = Modifier.padding(top = 8.dp),
            )

            val summary = Exploration.summaryLine(overview)
            if (summary != null || notice != null) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    summary?.let {
                        Text(
                            it,
                            Modifier.background(Color(0xE6FFFFFF), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    notice?.let {
                        Text(
                            it,
                            Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            if (loaded == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("読み込み中…") }
            } else {
                val base = PetAppearance.baseAnimation(pet.stats, config)
                val isEgg = pet.stage == Stage.EGG
                CareStage(
                    isEgg = isEgg,
                    riceCount = care.inventory.count(ItemType.RICE),
                    cleanliness = pet.stats.cleanliness,
                    field = CareSession.stainField,
                    friendVisiting = if (DemoFriends.hasFriends) "フレンドが遊びに来ている" else null,
                    onFeed = {
                        val consumed = progressStore.update { p ->
                            val inventory = p.inventory.consume(ItemType.RICE)
                            if (inventory == null) p to false else p.copy(inventory = inventory) to true
                        }
                        care = progressStore.load()
                        if (consumed) {
                            commit { PetSimulator.feed(it, now(), config.feedGain, config) }
                            play("eat")
                        }
                    },
                    onPet = {
                        commit { PetSimulator.pet(it, now(), config) }
                        play("happy")
                    },
                    onStainCleaned = {
                        commit { PetSimulator.clean(it, now(), config.cleanGainPerStain, config) }
                        pet.stats.cleanliness
                    },
                    modifier = Modifier.weight(1f),
                    statusBar = {
                        if (!isEgg) StatusBar(pet.stats.satiety, pet.stats.cleanliness, pet.stats.mood)
                    },
                ) { scale, widthPx, walking, facingLeft ->
                    val arriving = exploring && comeBack.value > 0f
                    // 戻ってくる間・歩き回っている間は walk アニメーション。向きは進行方向(左向きが -1)に合わせる
                    val flipped = arriving || (walking && facingLeft)
                    Box(Modifier.graphicsLayer { translationX = comeBack.value * widthPx; scaleX = if (flipped) -1f else 1f }) {
                        key(shotToken) {
                            SpritePlayer(
                                loaded,
                                animation = oneShot ?: if (arriving || walking) "walk" else base,
                                scale = scale,
                                onFrameChanged = { if (oneShot != null && it.animation == "idle") oneShot = null },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 探索中にこの画面を開いたとき、キャラクターが画面の外から戻ってくるまでの待ち時間と、歩いて戻る時間(8.6節) */
private const val RETURN_DELAY_MS = 3_000L
private const val RETURN_DURATION_MS = 2_500

/** 拠点の背景の飾り(仮。ワイヤーフレームの「廃屋」背景に準拠。本番のドット絵ができるまでの図形描画)。 */
@Composable
private fun LocationBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pillar = Color(0xFF8A7A6B)
        val pillarDark = Color(0xFF7A6B5D)
        fun rect(fx: Float, fy: Float, fw: Float, fh: Float, color: Color, alpha: Float) =
            drawRect(color, topLeft = Offset(w * fx, h * fy), size = androidx.compose.ui.geometry.Size(w * fw, h * fh), alpha = alpha)
        rect(0.077f, 0.521f, 0.067f, 0.261f, pillar, 0.55f)
        rect(0.231f, 0.450f, 0.077f, 0.332f, pillarDark, 0.6f)
        rect(0.692f, 0.486f, 0.072f, 0.296f, pillar, 0.5f)
        rect(0.821f, 0.545f, 0.062f, 0.237f, pillarDark, 0.55f)
        drawOval(
            Color(0xFF5C5145),
            topLeft = Offset(w * (0.5f - 0.590f), h * (0.829f - 0.047f)),
            size = androidx.compose.ui.geometry.Size(w * 0.590f * 2, h * 0.047f * 2),
            alpha = 0.4f,
        )
    }
}
