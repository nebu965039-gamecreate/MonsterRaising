package com.nebu965039.monsterraising.ui.demo

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.pet.DeathCause
import com.nebu965039.monsterraising.core.pet.PetAppearance
import com.nebu965039.monsterraising.core.pet.PetConfig
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.core.sprite.SpriteTimeline
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.ui.sprite.LoadedSprite
import com.nebu965039.monsterraising.ui.sprite.SpriteAssets
import com.nebu965039.monsterraising.ui.sprite.SpritePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val config = PetConfig()

// 餌の回復量は 4.1 の +20〜30 の中間。掃除は汚れ 1 箇所ぶん(10.2.2)。実際の汚れ操作は後のフェーズで実装する
private const val FEED_AMOUNT = 25.0

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS

private fun Stage.label() = when (this) {
    Stage.EGG -> "卵"
    Stage.INFANT -> "幼年期"
    Stage.GROWTH_1 -> "成長期Ⅰ"
    Stage.GROWTH_2 -> "成長期Ⅱ"
    Stage.MATURE -> "成熟期"
}

/**
 * Phase 2 の動作確認用画面。お世話→ステータス変化→進化までを試す。
 * 時間の経過は「時間を進める」ボタンで仮想時計を進めて再現する(端末の時計は変更しない)。
 * 見た目は全段階で同じ仮キャラを使う(段階ごとの見た目差分は未作成)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetDemoScreen(characterId: String = "fox") {
    val context = LocalContext.current
    val store = remember { PetStore(context) }
    val sprite by produceState<LoadedSprite?>(null, characterId) {
        value = withContext(Dispatchers.IO) { SpriteAssets.load(context, characterId) }
    }
    var offsetMs by remember { mutableLongStateOf(0L) }
    fun now() = System.currentTimeMillis() + offsetMs

    var pet by remember {
        mutableStateOf(PetSimulator.advance(store.load() ?: PetState.newEgg(now(), config), now(), config))
    }
    // 単発の演出(食事・なでる)。終わって idle に戻ったら基本アニメーションへ戻す
    var oneShot by remember { mutableStateOf<String?>(null) }
    var shotToken by remember { mutableIntStateOf(0) }

    var notice by remember { mutableStateOf<String?>(null) }
    fun commit(next: PetState) {
        if (next.generation > pet.generation) {
            notice = when (next.lastDeathCause) {
                DeathCause.OLD_AGE -> "寿命を迎えました。卵を残しました(${next.generation}代目。先代の有効度の5%を引き継ぎ)"
                else -> "放置により死亡しました。卵が残っていました(${next.generation}代目)"
            }
        }
        pet = next
    }

    LaunchedEffect(pet) { withContext(Dispatchers.IO) { store.save(pet) } }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            commit(PetSimulator.advance(pet, now(), config))
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
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val availablePx = with(LocalDensity.current) { (maxWidth * 0.6f).roundToPx() }
            val scale = SpriteTimeline.integerScale(availablePx, loaded.definition.size)
            Box(Modifier.background(Color(0xFFC8DCC8))) {
                key(shotToken) {
                    SpritePlayer(
                        loaded,
                        animation = oneShot ?: base,
                        scale = scale,
                        onFrameChanged = { if (oneShot != null && it.animation == "idle") oneShot = null },
                    )
                }
            }
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
                commit(PetSimulator.feed(pet, now(), FEED_AMOUNT, config))
                play("eat")
            }) { Text("餌(+${FEED_AMOUNT.toInt()})") }
            Button(enabled = !isEgg, onClick = { commit(PetSimulator.clean(pet, now(), config.cleanGainPerStain, config)) }) {
                Text("掃除(+${config.cleanGainPerStain.toInt()})")
            }
            Button(enabled = !isEgg, onClick = {
                commit(PetSimulator.pet(pet, now(), config))
                play("happy")
            }) { Text("なでる(+${config.petMoodGain.toInt()})") }
        }
        Text("デモ用の操作", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !isEgg, onClick = {
                commit(PetSimulator.act(pet, now(), config) { it.copy(satiety = 100.0, cleanliness = 100.0) })
            }) { Text("満腹度・清潔度を満タン") }
            OutlinedButton(enabled = !isEgg, onClick = {
                commit(PetSimulator.act(pet, now(), config) { it.addIntellect(50.0) })
            }) { Text("知力 +50") }
            OutlinedButton(enabled = !isEgg, onClick = {
                commit(PetSimulator.act(pet, now(), config) { it.addStrength(50.0) })
            }) { Text("筋力 +50") }
            OutlinedButton(enabled = pet.stage == Stage.MATURE, onClick = {
                commit(PetSimulator.extendLifespan(pet, now(), config))
            }) { Text("長寿の秘薬(+7日)") }
        }
        Text("時間を進める(仮想時計 +${offsetMs / HOUR_MS}時間)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { offsetMs += HOUR_MS; commit(PetSimulator.advance(pet, now(), config)) }) { Text("+1時間") }
            OutlinedButton(onClick = { offsetMs += DAY_MS; commit(PetSimulator.advance(pet, now(), config)) }) { Text("+1日") }
            OutlinedButton(onClick = { offsetMs += 60_000L; commit(PetSimulator.advance(pet, now(), config)) }) { Text("+1分") }
            OutlinedButton(onClick = {
                offsetMs = 0L
                store.clear()
                notice = null
                pet = PetState.newEgg(now(), config)
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
