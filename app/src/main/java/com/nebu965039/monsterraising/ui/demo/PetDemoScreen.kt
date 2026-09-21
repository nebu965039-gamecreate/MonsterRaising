package com.nebu965039.monsterraising.ui.demo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.care.CareSession
import com.nebu965039.monsterraising.core.exploration.Exploration
import com.nebu965039.monsterraising.core.minigame.ItemType
import com.nebu965039.monsterraising.core.pet.DeathCause
import com.nebu965039.monsterraising.core.pet.PetAppearance
import com.nebu965039.monsterraising.core.pet.PetConfig
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.core.sprite.SpriteTimeline
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.ui.care.CareStage
import com.nebu965039.monsterraising.ui.common.label
import com.nebu965039.monsterraising.widget.PetWidgetUpdater
import com.nebu965039.monsterraising.ui.sprite.LoadedSprite
import com.nebu965039.monsterraising.ui.sprite.SpriteAssets
import com.nebu965039.monsterraising.ui.sprite.SpritePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val config = PetConfig()

private const val HOUR_MS = 3_600_000L

/** 探索中にこの画面を開いたとき、キャラクターが画面の外から戻ってくるまでの待ち時間と、歩いて戻る時間(8.6節) */
private const val RETURN_DELAY_MS = 3_000L
private const val RETURN_DURATION_MS = 2_500
private const val DAY_MS = 24 * HOUR_MS

/**
 * Phase 2 の動作確認用画面。お世話→ステータス変化→進化までを試す。
 * 時間の経過は「時間を進める」ボタンで仮想時計を進めて再現する(端末の時計は変更しない)。
 * 見た目は全段階で同じ仮キャラを使う(段階ごとの見た目差分は未作成)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetDemoScreen(characterId: String = "fox", resumeTick: Int = 0) {
    val context = LocalContext.current
    val store = remember { PetStore(context) }
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val loaded = sprite
        if (loaded == null) {
            Text("読み込み中…")
            return@Column
        }
        val base = PetAppearance.baseAnimation(pet.stats, config)
        // メイン画面のお世話操作(10.2節): なでる(キャラクターをタップ)・餌やり(ごはんをドラッグ&ドロップ)・掃除(汚れをなでる)
        CareStage(
            isEgg = pet.stage == Stage.EGG,
            riceCount = care.inventory.count(ItemType.RICE),
            cleanliness = pet.stats.cleanliness,
            field = CareSession.stainField,
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
        ) { scale, widthPx ->
            val arriving = exploring && comeBack.value > 0f
            // 戻ってくる間は、左向き(進行方向)にして歩く
            Box(Modifier.graphicsLayer { translationX = comeBack.value * widthPx; scaleX = if (arriving) -1f else 1f }) {
                key(shotToken) {
                    SpritePlayer(
                        loaded,
                        animation = if (arriving) "walk" else (oneShot ?: base),
                        scale = scale,
                        onFrameChanged = { if (oneShot != null && it.animation == "idle") oneShot = null },
                    )
                }
            }
        }
        Exploration.summaryLine(overview)?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        }
        Text(
            "${pet.generation}代目  段階: ${pet.stage.label()}   (表示: ${oneShot ?: base})",
            style = MaterialTheme.typography.titleMedium,
        )
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        val isEgg = pet.stage == Stage.EGG
        if (isEgg) {
            Text("卵の間はステータスがありません(幼年期になると初期値が付きます)", style = MaterialTheme.typography.bodyMedium)
            if (pet.stats.intellect > 0 || pet.stats.strength > 0) {
                Text(
                    "先代からの引き継ぎ  知力 +${"%.1f".format(pet.stats.intellect)} / 筋力 +${"%.1f".format(pet.stats.strength)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Gauge("満腹度", pet.stats.satiety)
            Gauge("清潔度", pet.stats.cleanliness)
            Gauge("機嫌", pet.stats.mood)
            Text(
                "有効度  知力 ${pet.stats.intellect.toInt()} / 筋力 ${pet.stats.strength.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            pet.lifespanEndMs()?.let { end ->
                val left = (end - now()).coerceAtLeast(0L)
                Text(
                    "寿命まで あと ${left / DAY_MS}日${left % DAY_MS / HOUR_MS}時間(延長 +${pet.lifespanExtensionMs / DAY_MS}日)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !isEgg, onClick = {
                commit { PetSimulator.feed(it, now(), config.feedGain, config) }
                play("eat")
            }) { Text("餌(デモ・ごはん消費なし +${config.feedGain.toInt()})") }
            Button(enabled = !isEgg, onClick = { commit { PetSimulator.clean(it, now(), config.cleanGainPerStain, config) } }) {
                Text("掃除(+${config.cleanGainPerStain.toInt()})")
            }
            Button(enabled = !isEgg, onClick = {
                commit { PetSimulator.pet(it, now(), config) }
                play("happy")
            }) { Text("なでる(+${config.petMoodGain.toInt()})") }
        }
        Text("デモ用の操作", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !isEgg, onClick = {
                commit { PetSimulator.act(it, now(), config) { s -> s.copy(satiety = 100.0, cleanliness = 100.0) } }
            }) { Text("満腹度・清潔度を満タン") }
            OutlinedButton(enabled = !isEgg, onClick = {
                commit { PetSimulator.act(it, now(), config) { s -> s.addIntellect(50.0) } }
            }) { Text("知力 +50") }
            OutlinedButton(enabled = !isEgg, onClick = {
                commit { PetSimulator.act(it, now(), config) { s -> s.addStrength(50.0) } }
            }) { Text("筋力 +50") }
            OutlinedButton(enabled = pet.stage == Stage.MATURE, onClick = {
                commit { PetSimulator.extendLifespan(it, now(), config) }
            }) { Text("長寿の秘薬(+7日)") }
        }
        Text("時間を進める(仮想時計 +${DemoClock.offsetMs / HOUR_MS}時間)", style = MaterialTheme.typography.labelLarge)
        if (DemoClock.offsetMs != 0L) {
            Text(
                "仮想時計の使用中は、実時間で動くウィジェットの表示と食い違います(「卵からやり直す」で戻ります)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { DemoClock.offsetMs += HOUR_MS; commit { PetSimulator.advance(it, now(), config) } }) { Text("+1時間") }
            OutlinedButton(onClick = { DemoClock.offsetMs += DAY_MS; commit { PetSimulator.advance(it, now(), config) } }) { Text("+1日") }
            OutlinedButton(onClick = { DemoClock.offsetMs += 60_000L; commit { PetSimulator.advance(it, now(), config) } }) { Text("+1分") }
            OutlinedButton(onClick = {
                DemoClock.offsetMs = 0L
                store.clear()
                notice = null
                commit { PetState.newEgg(now(), config) }
            }) { Text("卵からやり直す") }
        }
        Text(
            "進化は「段階の期間を満たした時点」で満腹度・清潔度が60以上のときに起こります(卵は時間だけで孵化)。" +
                "満たさない間は保留です(1回の更新で進むのは1段階まで)。満腹度0が24時間続き、清潔度が20以下になると死亡し、卵に戻ります。成熟期は2週間で寿命を迎え、先代の有効度の5%を引き継いだ卵が残ります。放置中に進化の時刻が来ていても遡らず、開いた時点の値で判定します。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Gauge(label: String, value: Double) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { (value / 100.0).toFloat() }, modifier = Modifier.weight(1f))
        Text("%.1f".format(value), Modifier.width(56.dp).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
    }
}
