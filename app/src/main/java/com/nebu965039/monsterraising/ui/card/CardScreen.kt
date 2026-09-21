package com.nebu965039.monsterraising.ui.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.minigame.CardRewards
import com.nebu965039.monsterraising.core.minigame.ClearRewards
import com.nebu965039.monsterraising.core.minigame.DayClock
import com.nebu965039.monsterraising.core.minigame.MiniGame
import com.nebu965039.monsterraising.core.minigame.MinigameGains
import com.nebu965039.monsterraising.core.minigame.MonsterCards
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.data.CardRecordStore
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.minigame.card.Attribute
import com.nebu965039.monsterraising.minigame.card.Card
import com.nebu965039.monsterraising.minigame.card.CardMatch
import com.nebu965039.monsterraising.minigame.card.MatchOutcome
import com.nebu965039.monsterraising.minigame.card.MatchResult
import com.nebu965039.monsterraising.minigame.card.MonsterCard
import com.nebu965039.monsterraising.minigame.card.NpcLevel
import com.nebu965039.monsterraising.minigame.card.Phase
import com.nebu965039.monsterraising.minigame.card.RoundOutcome
import com.nebu965039.monsterraising.minigame.card.RoundWinner
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.ui.minigame.PlayLimitPanel
import com.nebu965039.monsterraising.ui.minigame.rewardsText
import com.nebu965039.monsterraising.widget.PetWidgetUpdater

/** 1 マッチが終わったあとに表示する内容。 */
private data class MatchSummary(
    val result: MatchResult,
    val gains: MinigameGains,
    val applied: Boolean,
    val streak: Int,
    val bestStreak: Int,
    val newlyUnlocked: NpcLevel?,
    val capped: Boolean,
    val rewards: ClearRewards?,
)

private fun NpcLevel.label() = when (this) {
    NpcLevel.BEGINNER -> "初級"
    NpcLevel.INTERMEDIATE -> "中級"
    NpcLevel.ADVANCED -> "上級"
}

private fun MonsterCard.label() = when (this) {
    MonsterCard.PEEK -> "先読み"
    MonsterCard.REDRAW -> "引き直し"
    MonsterCard.STEADY -> "見極め"
    MonsterCard.ENDURE -> "耐える"
}

private fun MonsterCard.description() = when (this) {
    MonsterCard.PEEK -> "相手の伏せ札を 1 枚見られる(1 戦に 1 回)"
    MonsterCard.REDRAW -> "直前に引いたカードを 1 回だけ引き直せる"
    MonsterCard.STEADY -> "次に引くとき 2 枚引いて、目標値に近いほうを選べる(1 戦に 1 回)"
    MonsterCard.ENDURE -> "目標値を超えたとき 1 回だけ耐える(超えたカードを捨てて止める。自動で発動)"
}

/** カード×NPC戦(基本設計書6節)の画面。難易度(NPC)を選び、5 ラウンドのコイン戦を遊ぶ。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardScreen() {
    val context = LocalContext.current
    val petStore = remember { PetStore(context) }
    val recordStore = remember { CardRecordStore(context) }
    val progressStore = remember { MiniGameStore(context) }
    var progress by remember { mutableStateOf(progressStore.load()) }
    var records by remember { mutableStateOf(recordStore.load()) }
    var level by remember { mutableStateOf(NpcLevel.BEGINNER) }
    var match by remember { mutableStateOf<CardMatch?>(null) }
    var summary by remember { mutableStateOf<MatchSummary?>(null) }
    // 手札などの状態はゲームの中(Compose の状態ではない)にあるので、操作のたびに増やして再描画させる
    var tick by remember { mutableIntStateOf(0) }
    fun day() = DayClock.dayIndex(DemoClock.now())

    val pet = petStore.load()
    val isEgg = pet?.stage == Stage.EGG
    val monsterCard = pet?.let { MonsterCards.forPet(it) }

    fun startMatch(target: NpcLevel) {
        val started = progressStore.update { p ->
            val next = p.startPlay(MiniGame.CARD, day())
            if (next == null) p to false else next to true
        }
        progress = progressStore.load()
        if (started) match = CardMatch(target, MonsterCards.forPet(petStore.load() ?: return))
    }

    fun finish(result: MatchResult) {
        val update = recordStore.record(result)
        records = update.records
        val settlement = progressStore.update { p ->
            val s = p.settle(
                MiniGame.CARD, day(), CardRewards.tier(result.level),
                cleared = CardRewards.isClear(result), firstClear = update.isFirstClear, gains = CardRewards.gains(result),
            )
            s.progress to s
        }
        progress = settlement.progress
        val before = petStore.load()
        val after = petStore.update(DemoClock.now()) { PetSimulator.applyMinigame(it, DemoClock.now(), settlement.appliedGains) }
        summary = MatchSummary(
            result, settlement.appliedGains,
            applied = before != null && before.stage != Stage.EGG && after.generation == before.generation,
            streak = update.records.streak,
            bestStreak = update.records.bestStreak,
            newlyUnlocked = update.newlyUnlocked,
            capped = settlement.capped,
            rewards = settlement.rewards,
        )
        PetWidgetUpdater.updateAll(context)
    }

    val current = match
    val done = summary
    when {
        current != null && done == null -> PlayView(
            current, tick,
            onChanged = { tick++ },
            onNextRound = {
                current.nextRound()
                tick++
                if (current.isMatchOver) finish(current.result()!!)
            },
        )
        done != null -> ResultView(
            done,
            canRetry = progress.playStatus(MiniGame.CARD, day()).remaining > 0 && records.isUnlocked(done.result.level),
            onRetry = {
                summary = null
                level = done.result.level
                match = null
                startMatch(level)
            },
            onMenu = { summary = null; match = null },
        )
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("カード × NPC 戦", style = MaterialTheme.typography.titleLarge)
            Text(
                "お互いにコイン 5 枚で始め、ラウンドごとに 1〜3 枚を賭けます。カードの合計を 20 に近づけた側の勝ちで、超えると負けです。" +
                    "5 ラウンドを終えて(またはコインが尽きて)コインが多い側の勝ち。勝つと知力系の有効度が上がります。",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NpcLevel.entries.forEach { l ->
                    FilterChip(
                        selected = l == level,
                        enabled = records.isUnlocked(l),
                        onClick = { level = l },
                        label = { Text(if (records.isUnlocked(l)) "${l.label()}(${l.stopAt}以上で止める)" else "${l.label()}(未解放)") },
                    )
                }
            }
            NpcSilhouette(level, Modifier.size(96.dp))
            Text(
                "連勝 ${records.streak}(最高 ${records.bestStreak})  ${if (records.hasCleared(level)) "クリア済み" else "未クリア"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (monsterCard != null) {
                Text("モンスターカード: ${monsterCard.label()} — ${monsterCard.description()}", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("卵の間はモンスターカードを持てません。", style = MaterialTheme.typography.bodySmall)
            }
            if (isEgg) Text("卵の間は遊べません。", color = MaterialTheme.colorScheme.error)
            PlayLimitPanel(
                status = progress.playStatus(MiniGame.CARD, day()),
                progress = progress,
                startEnabled = !isEgg && records.isUnlocked(level),
                onStart = { startMatch(level) },
                onWatchAd = {
                    // 広告は Phase 8 で実装する。それまではデモとして、広告なしで 1 回追加する
                    progressStore.update { p -> (p.addAdPlay(MiniGame.CARD, day()) ?: p) to Unit }
                    progress = progressStore.load()
                },
                onUseTicket = {
                    progressStore.update { p -> (p.addTicketPlay(MiniGame.CARD, day()) ?: p) to Unit }
                    progress = progressStore.load()
                },
            )
        }
    }
}

/** [tick] は使わないが、変わるたびに再コンポーズさせるための引数(ゲームの状態は Compose の外にあるため)。 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayView(match: CardMatch, tick: Int, onChanged: () -> Unit, onNextRound: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${match.level.label()}   ラウンド ${match.round} / ${match.config.maxRounds}", style = MaterialTheme.typography.titleMedium)
            if (match.phase != Phase.Betting) Text("賭け ${match.bet}", style = MaterialTheme.typography.titleMedium)
        }

        // 相手
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NpcSilhouette(match.level, Modifier.size(72.dp))
            Column {
                Text("相手  コイン ${match.npcCoins}", style = MaterialTheme.typography.titleMedium)
                if (match.phase == Phase.PlayerTurn || match.phase == Phase.RoundOver) {
                    val shown = match.npcCardsShown()
                    Text(
                        if (shown.any { it == null }) "見えている合計 ${match.npcShownTotal}(伏せ札あり)" else "合計 ${match.npcTotal}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            match.npcCardsShown().forEach { CardFace(it) }
        }

        // 自分
        Text("あなた  コイン ${match.playerCoins}   合計 ${match.playerTotal}(目標 ${match.config.target})", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            match.playerHand.forEach { CardFace(it) }
        }

        // 操作
        when (match.phase) {
            Phase.Betting -> {
                Text("賭けるコインを選んでください", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..match.config.maxBet).forEach { n ->
                        Button(enabled = n <= match.maxBet(), onClick = { if (match.placeBet(n)) onChanged() }) { Text("$n 枚") }
                    }
                }
            }
            Phase.PlayerTurn -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (match.draw()) onChanged() }) { Text("引く") }
                    Button(onClick = { if (match.stand()) onChanged() }) { Text("止める") }
                }
                match.monsterCard?.let { mc ->
                    if (mc == MonsterCard.ENDURE) {
                        Text(
                            "モンスターカード「${mc.label()}」: " + if (match.monsterCardUsed) "使用済み" else "待機中(超えたとき自動で発動)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        OutlinedButton(enabled = match.canUseMonsterCard(), onClick = { if (match.useMonsterCard()) onChanged() }) {
                            Text("モンスターカード「${mc.label()}」" + if (match.monsterCardUsed) "(使用済み)" else "")
                        }
                        Text(mc.description(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Phase.RoundOver -> {
                match.lastOutcome?.let { Text(roundMessage(it), style = MaterialTheme.typography.titleMedium) }
                Button(onClick = onNextRound) { Text(if (match.endsAfterThisRound()) "結果へ" else "次のラウンドへ") }
            }
            Phase.MatchOver -> Unit
        }
    }
}

private fun roundMessage(o: RoundOutcome): String {
    val outcome = when (o.winner) {
        RoundWinner.PLAYER -> "あなたの勝ち(コイン +${o.bet})"
        RoundWinner.NPC -> "あなたの負け(コイン -${o.bet})"
        RoundWinner.DRAW -> "引き分け(コインは動きません)"
    }
    return when {
        o.playerBust -> "超過!(${o.playerTotal})  $outcome"
        o.npcBust -> "相手が超過(${o.npcTotal})  $outcome"
        o.endured -> "耐えた! ${o.playerTotal} で止めました  ${o.playerTotal} 対 ${o.npcTotal}  $outcome"
        else -> "${o.playerTotal} 対 ${o.npcTotal}  $outcome"
    }
}

@Composable
private fun ResultView(summary: MatchSummary, canRetry: Boolean, onRetry: () -> Unit, onMenu: () -> Unit) {
    val r = summary.result
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when (r.outcome) {
                MatchOutcome.WIN -> "勝利!"
                MatchOutcome.LOSE -> "敗北"
                MatchOutcome.DRAW -> "無効試合(引き分け)"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text("${r.level.label()}   コイン あなた ${r.playerCoins} 対 相手 ${r.npcCoins}(${r.roundsPlayed} ラウンド)", style = MaterialTheme.typography.titleMedium)
        r.monsterCard?.let {
            Text("モンスターカード「${it.label()}」: ${if (r.monsterCardUsed) "使った" else "使わなかった"}", style = MaterialTheme.typography.bodyMedium)
        }
        Text("連勝 ${summary.streak}(最高 ${summary.bestStreak})", style = MaterialTheme.typography.bodyMedium)
        summary.newlyUnlocked?.let { Text("${it.label()} が解放されました!", color = MaterialTheme.colorScheme.primary) }
        Text(
            if (summary.applied) {
                "育成に反映: 知力 +${"%.0f".format(summary.gains.intellect)} / 機嫌 +${summary.gains.mood.toInt()}"
            } else {
                "育成には反映されませんでした(卵、または遊んでいる間に世代が替わりました)"
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (summary.capped) {
            Text("今日の有効度の上限に達したため、加算が一部(または全部)反映されませんでした", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        summary.rewards?.let {
            Text("クリア報酬: ${rewardsText(it)}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = canRetry, onClick = onRetry) { Text("もう一度") }
            OutlinedButton(onClick = onMenu) { Text("メニューへ") }
        }
    }
}

// --- カードと NPC の描画 ---

private fun Attribute.symbol() = when (this) {
    Attribute.SUNNY -> "☀"
    Attribute.CLOUDY -> "☁"
    Attribute.RAIN -> "☂"
    Attribute.SNOW -> "❄"
    Attribute.STORM -> "⚡"
}

/** 属性の色(天候アイコンをそのままカードの意匠に流用する。6.2.1節)。 */
private fun Attribute.color() = when (this) {
    Attribute.SUNNY -> Color(0xFFF5A623)
    Attribute.CLOUDY -> Color(0xFF7F93A6)
    Attribute.RAIN -> Color(0xFF3F7FE0)
    Attribute.SNOW -> Color(0xFF3FB6D6)
    Attribute.STORM -> Color(0xFF9C5FD0)
}

/** カード 1 枚。[card] が null なら伏せ札。 */
@Composable
private fun CardFace(card: Card?) {
    val shape = RoundedCornerShape(8.dp)
    if (card == null) {
        Box(Modifier.size(48.dp, 68.dp).background(Color(0xFF3A2E2E), shape).border(BorderStroke(2.dp, Color(0xFF1B1212)), shape), contentAlignment = Alignment.Center) {
            Text("?", color = Color(0xFFE53935), style = MaterialTheme.typography.titleLarge)
        }
    } else {
        Box(Modifier.size(48.dp, 68.dp).background(Color(0xFFFFFBF2), shape).border(BorderStroke(2.dp, card.attribute.color()), shape), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(card.attribute.symbol(), color = card.attribute.color(), style = MaterialTheme.typography.titleMedium)
                Text("${card.value}", color = Color(0xFF2B1B12), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/**
 * NPC の見た目(6.3節): 黒い靄のような人影+赤い目。中級で笑ったような赤い口、上級でさらに悪魔のような角が加わる。
 * 本番のドット絵ができるまでの仮の描画。
 */
@Composable
private fun NpcSilhouette(level: NpcLevel, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawNpc(level) }
}

private fun DrawScope.drawNpc(level: NpcLevel) {
    val w = size.width
    val h = size.height
    val black = Color(0xFF14101A)
    // 靄
    drawCircle(Color(0x33141018), radius = w * 0.46f, center = Offset(w * 0.5f, h * 0.58f))
    drawCircle(Color(0x33141018), radius = w * 0.40f, center = Offset(w * 0.32f, h * 0.7f))
    drawCircle(Color(0x33141018), radius = w * 0.40f, center = Offset(w * 0.68f, h * 0.7f))
    // 体と頭
    drawCircle(black, radius = w * 0.30f, center = Offset(w * 0.5f, h * 0.52f))
    drawRect(black, Offset(w * 0.24f, h * 0.62f), Size(w * 0.52f, h * 0.34f))
    // 角(上級)
    if (level == NpcLevel.ADVANCED) {
        for (side in listOf(-1f, 1f)) {
            val path = Path().apply {
                moveTo(w * (0.5f + side * 0.14f), h * 0.32f)
                lineTo(w * (0.5f + side * 0.30f), h * 0.04f)
                lineTo(w * (0.5f + side * 0.04f), h * 0.26f)
                close()
            }
            drawPath(path, black)
        }
    }
    // 赤い目
    val red = Color(0xFFE53935)
    drawCircle(red, radius = w * 0.045f, center = Offset(w * 0.40f, h * 0.48f))
    drawCircle(red, radius = w * 0.045f, center = Offset(w * 0.60f, h * 0.48f))
    // 笑ったような赤い口(中級以上)
    if (level != NpcLevel.BEGINNER) {
        drawArc(
            red, startAngle = 10f, sweepAngle = 160f, useCenter = false,
            topLeft = Offset(w * 0.36f, h * 0.50f), size = Size(w * 0.28f, h * 0.16f),
            style = Stroke(width = w * 0.03f),
        )
    }
}
