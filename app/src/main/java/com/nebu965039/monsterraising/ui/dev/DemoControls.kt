package com.nebu965039.monsterraising.ui.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.pet.DeathCause
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.PetState
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.data.careConfig
import com.nebu965039.monsterraising.ui.common.label
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.widget.PetWidgetUpdater

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS

/**
 * 育成ロジックの動作確認用の操作(数値を直接確認しながら、餌やり・掃除・なでる・仮想時計を試す)。
 * 本番の操作(ドラッグ&ドロップの餌やり・なぞる掃除)はメイン画面([com.nebu965039.monsterraising.ui.home.HomeScreen])にある。
 * ここはメイン画面のヘッダーのメニュー「開発用」から開く。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoControls() {
    val context = LocalContext.current
    val store = remember { PetStore(context) }
    val config = remember { careConfig(context) }
    fun now() = DemoClock.now()

    var pet by remember { mutableStateOf(store.update(now(), config) { PetSimulator.advance(it, now(), config) }) }
    var notice by remember { mutableStateOf<String?>(null) }

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

    val isEgg = pet.stage == Stage.EGG

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${pet.generation}代目  段階: ${pet.stage.label()}",
            style = MaterialTheme.typography.titleMedium,
        )
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
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
            Button(enabled = !isEgg, onClick = { commit { PetSimulator.feed(it, now(), config.feedGain, config) } }) {
                Text("餌(デモ・ごはん消費なし +${config.feedGain.toInt()})")
            }
            Button(enabled = !isEgg, onClick = { commit { PetSimulator.clean(it, now(), config.cleanGainPerStain, config) } }) {
                Text("掃除(+${config.cleanGainPerStain.toInt()})")
            }
            Button(enabled = !isEgg, onClick = { commit { PetSimulator.pet(it, now(), config) } }) {
                Text("なでる(+${config.petMoodGain.toInt()})")
            }
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
        Text("%.1f".format(value), Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
    }
}
