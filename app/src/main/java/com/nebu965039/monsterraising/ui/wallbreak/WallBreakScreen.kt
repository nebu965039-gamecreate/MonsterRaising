package com.nebu965039.monsterraising.ui.wallbreak

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nebu965039.monsterraising.core.effects.BurstParticles
import com.nebu965039.monsterraising.core.minigame.ClearRewards
import com.nebu965039.monsterraising.core.minigame.DayClock
import com.nebu965039.monsterraising.core.minigame.MiniGame
import com.nebu965039.monsterraising.core.minigame.MinigameGains
import com.nebu965039.monsterraising.core.minigame.MissNullify
import com.nebu965039.monsterraising.core.minigame.WallBreakRewards
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.core.sprite.SpriteTimeline
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.data.careConfig
import com.nebu965039.monsterraising.data.ScoreRecordStore
import com.nebu965039.monsterraising.minigame.wallbreak.ChoiceOutcome
import com.nebu965039.monsterraising.minigame.wallbreak.RuleKind
import com.nebu965039.monsterraising.minigame.wallbreak.WallBreakGame
import com.nebu965039.monsterraising.minigame.wallbreak.WallBreakResult
import com.nebu965039.monsterraising.minigame.wallbreak.WallColor
import com.nebu965039.monsterraising.minigame.wallbreak.WallDifficulty
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.ui.minigame.PlayLimitPanel
import com.nebu965039.monsterraising.ui.minigame.rewardsText
import com.nebu965039.monsterraising.ui.sprite.LoadedSprite
import com.nebu965039.monsterraising.ui.sprite.SpriteAssets
import com.nebu965039.monsterraising.ui.sprite.SpritePlayer
import com.nebu965039.monsterraising.widget.PetWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RECORD_FILE = "wallbreak_records.json"
private const val MAX_FRAME_MS = 100L

/** 壁が砕ける演出 1 件分。[xFraction] は壁の並びの左端を 0、右端を 1 とした位置。 */
private data class BurstEvent(val seed: Int, val color: WallColor, val xFraction: Float, val startMs: Long)

/** 1 プレイが終わったあとに表示する内容。 */
private data class WallBreakSummary(
    val result: WallBreakResult,
    val gains: MinigameGains,
    val applied: Boolean,
    val isNewHighScore: Boolean,
    val isFirstClear: Boolean,
    val capped: Boolean,
    val rewards: ClearRewards?,
)

private fun WallDifficulty.label() = when (this) {
    WallDifficulty.BEGINNER -> "初級"
    WallDifficulty.INTERMEDIATE -> "中級"
    WallDifficulty.ADVANCED -> "上級"
}

private fun WallDifficulty.detail() = "${label()}(壁 $wallCount 枚・${palette.size} 色・目標 $targetWalls 枚)"

private fun RuleKind.headline() = when (this) {
    RuleKind.INK -> "文字色ルール: 文字の「色」に合う壁を選べ"
    RuleKind.WORD -> "単語ルール: 書かれた「意味」に合う壁を選べ"
}

/** 壁と指令の色。黒は背景から浮かないよう、壁に明るい縁取りを付ける。 */
private fun WallColor.toColor(): Color = when (this) {
    WallColor.RED -> Color(0xFFE04B4B)
    WallColor.YELLOW -> Color(0xFFF2D338)
    WallColor.BLUE -> Color(0xFF3F7FE0)
    WallColor.GREEN -> Color(0xFF4CAF50)
    WallColor.BLACK -> Color(0xFF1A1A1A)
    WallColor.PURPLE -> Color(0xFF9C5FD0)
    WallColor.ORANGE -> Color(0xFFF59A2B)
}

/** 壁破りゲーム(基本設計書7節)の画面。指令に合う色の壁を選んで、モンスターが壁を破壊しながら前進する。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallBreakScreen() {
    val context = LocalContext.current
    val petStore = remember { PetStore(context) }
    val recordStore = remember { ScoreRecordStore(context, RECORD_FILE) }
    val progressStore = remember { MiniGameStore(context) }
    var progress by remember { mutableStateOf(progressStore.load()) }
    var records by remember { mutableStateOf(recordStore.load()) }
    var difficulty by remember { mutableStateOf(WallDifficulty.BEGINNER) }
    var game by remember { mutableStateOf<WallBreakGame?>(null) }
    var summary by remember { mutableStateOf<WallBreakSummary?>(null) }
    fun day() = DayClock.dayIndex(DemoClock.now())

    val pet = petStore.load()
    val isEgg = pet?.stage == Stage.EGG
    val nullify = pet?.let { MissNullify.forPet(it) } ?: 0

    fun startGame(target: WallDifficulty) {
        val started = progressStore.update { p ->
            val next = p.startPlay(MiniGame.WALL_BREAK, day())
            if (next == null) p to false else next to true
        }
        progress = progressStore.load()
        if (started) game = WallBreakGame(target, nullifyCount = MissNullify.forPet(petStore.load() ?: return))
    }

    fun finish(result: WallBreakResult) {
        val update = recordStore.record(result.difficulty.name, result.score, result.cleared)
        records = update.records
        val settlement = progressStore.update { p ->
            val s = p.settle(
                MiniGame.WALL_BREAK, day(), WallBreakRewards.tier(result),
                cleared = result.cleared, firstClear = update.isFirstClear, gains = WallBreakRewards.gains(result),
            )
            s.progress to s
        }
        progress = settlement.progress
        val before = petStore.load()
        val config = careConfig(context)
        val after = petStore.update(DemoClock.now(), config) { PetSimulator.applyMinigame(it, DemoClock.now(), settlement.appliedGains, config) }
        summary = WallBreakSummary(
            result, settlement.appliedGains,
            applied = before != null && before.stage != Stage.EGG && after.generation == before.generation,
            isNewHighScore = update.isNewHighScore,
            isFirstClear = update.isFirstClear,
            capped = settlement.capped,
            rewards = settlement.rewards,
        )
        PetWidgetUpdater.updateAll(context)
    }

    val current = game
    val done = summary
    when {
        current != null && done == null -> PlayView(current, onFinished = { finish(it) })
        done != null -> ResultView(
            done,
            canRetry = progress.playStatus(MiniGame.WALL_BREAK, day()).remaining > 0,
            onRetry = {
                summary = null
                difficulty = done.result.difficulty
                game = null
                startGame(difficulty)
            },
            onMenu = { summary = null; game = null },
        )
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("壁破りゲーム", style = MaterialTheme.typography.titleLarge)
            Text(
                "制限時間 60 秒。色名の単語が、意味とは違う色の文字で表示されます。" +
                    "プレイ開始時に決まるルール(文字の色 / 書かれた意味)に合う壁を選んで、破壊しながら進みます。" +
                    "間違えると残り時間が 3 秒減ります。結果は筋力系の有効度と機嫌に反映されます。",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallDifficulty.entries.forEach { d ->
                    FilterChip(selected = d == difficulty, onClick = { difficulty = d }, label = { Text(d.detail()) })
                }
            }
            Text(
                "ハイスコア ${records.highScore(difficulty.name)}   ${if (records.hasCleared(difficulty.name)) "クリア済み" else "未クリア"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (isEgg) {
                    "卵の間は遊べません。"
                } else {
                    "ミス無効: $nullify 回(筋力系有効度 100 ごとに 1 回。成長期Ⅰ 最大 1 回・成長期Ⅱ 最大 2 回・成熟期 最大 3 回)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isEgg) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            PlayLimitPanel(
                status = progress.playStatus(MiniGame.WALL_BREAK, day()),
                progress = progress,
                startEnabled = !isEgg,
                onStart = { startGame(difficulty) },
                onWatchAd = {
                    // 広告は Phase 8 で実装する。それまではデモとして、広告なしで 1 回追加する
                    progressStore.update { p -> (p.addAdPlay(MiniGame.WALL_BREAK, day()) ?: p) to Unit }
                    progress = progressStore.load()
                },
                onUseTicket = {
                    progressStore.update { p -> (p.addTicketPlay(MiniGame.WALL_BREAK, day()) ?: p) to Unit }
                    progress = progressStore.load()
                },
            )
        }
    }
}

@Composable
private fun PlayView(game: WallBreakGame, onFinished: (WallBreakResult) -> Unit) {
    val context = LocalContext.current
    val sprite by produceState<LoadedSprite?>(null) {
        value = withContext(Dispatchers.IO) { SpriteAssets.load(context, "fox") }
    }
    // ゲームは画面の外の状態(Compose の状態ではない)なので、値を増やして再描画させる
    var frame by remember(game) { mutableIntStateOf(0) }
    var feedback by remember(game) { mutableStateOf<ChoiceOutcome?>(null) }
    // 単発の演出(攻撃・ダメージ)。終わって idle に戻ったら、前進(walk)に戻す
    var oneShot by remember(game) { mutableStateOf<String?>(null) }
    var shotToken by remember(game) { mutableIntStateOf(0) }
    val finished by rememberUpdatedState(onFinished)
    val wallImages = remember { WallImages.load(context) }
    // 壁が砕ける演出。時計はフレームごとに進め、終わった演出は取り除く
    var clockMs by remember(game) { mutableLongStateOf(0L) }
    var bursts by remember(game) { mutableStateOf(listOf<BurstEvent>()) }
    var burstCount by remember(game) { mutableIntStateOf(0) }

    LaunchedEffect(game) {
        var last = -1L
        while (!game.isFinished) {
            withFrameMillis { now ->
                if (last >= 0) game.tick(minOf(now - last, MAX_FRAME_MS))
                last = now
                clockMs = now
                if (bursts.isNotEmpty()) bursts = bursts.filter { now - it.startMs < BurstParticles.DURATION_MS }
                frame++
            }
        }
        frame++
        finished(game.result())
    }

    fun choose(index: Int) {
        val wallColor = game.prompt.walls.getOrNull(index)
        val wallCount = game.prompt.walls.size
        val outcome = game.choose(index)
        if (outcome == ChoiceOutcome.IGNORED) return
        if (wallColor != null && outcome != ChoiceOutcome.MISS) {
            burstCount++
            bursts = bursts + BurstEvent(burstCount, wallColor, (index + 0.5f) / wallCount, clockMs)
        }
        feedback = outcome
        oneShot = if (outcome == ChoiceOutcome.MISS) "damage" else "attack"
        shotToken++
        frame++
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val f = frame // 読み取って再コンポーズさせる
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("突破 ${game.wallsBroken} / ${game.difficulty.targetWalls}", style = MaterialTheme.typography.titleMedium)
            Text("残り ${(game.timeLeftMs + 999) / 1000} 秒", style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("スコア ${game.score}   ミス無効 ${game.nullifyLeft}", style = MaterialTheme.typography.bodyMedium)
            Text(if (game.combo >= 2) "${game.combo} コンボ" else "", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        }
        Text(game.rule.headline(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        // 指令: 色名の単語を、意味とは異なるインク色で表示する
        val prompt = game.prompt
        Box(
            Modifier.fillMaxWidth().height(96.dp).background(Color(0xFFC5CCD3), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                prompt.word,
                color = prompt.ink.toColor(),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                style = TextStyle(shadow = Shadow(Color(0x99000000), Offset(2f, 2f), 4f)),
            )
        }

        // モンスターと結果の表示
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val loaded = sprite
            if (loaded != null) {
                val availablePx = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.9f).roundToPx() }
                val scale = SpriteTimeline.integerScale(availablePx, loaded.definition.size)
                key(shotToken) {
                    SpritePlayer(
                        loaded,
                        animation = oneShot ?: "walk",
                        scale = scale,
                        onFrameChanged = { if (oneShot != null && it.animation == "idle") oneShot = null },
                    )
                }
            }
            Text(
                when (feedback) {
                    ChoiceOutcome.CORRECT -> "壁を破壊!"
                    ChoiceOutcome.NULLIFIED -> "ミス無効! 時間は減りません"
                    ChoiceOutcome.MISS -> "衝突! 残り時間 -3 秒"
                    else -> ""
                },
                Modifier.align(Alignment.TopCenter),
                style = MaterialTheme.typography.titleMedium,
                color = if (feedback == ChoiceOutcome.MISS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }

        // 壁(正面に並ぶ)。壊れた壁は、破片がはじけ飛ぶ
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                prompt.walls.forEachIndexed { i, color ->
                    val edge = if (color == WallColor.BLACK) Color(0xFFEEEEEE) else Color(0xFF2B1B12)
                    val shape = RoundedCornerShape(10.dp)
                    val image = wallImages[color]
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(shape)
                            .background(color.toColor(), shape)
                            .clickable(enabled = f >= 0 && !game.isFinished) { choose(i) },
                    ) {
                        if (image != null) {
                            Image(
                                bitmap = image,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds,
                                filterQuality = FilterQuality.None,
                            )
                        }
                        Box(Modifier.fillMaxSize().border(BorderStroke(4.dp, edge), shape))
                    }
                }
            }
            Canvas(Modifier.matchParentSize()) {
                for (b in bursts) {
                    val cx = size.width * b.xFraction
                    val cy = size.height / 2f
                    val unit = size.height
                    for (fr in BurstParticles.fragments(b.seed, clockMs - b.startMs)) {
                        val side = fr.size * unit
                        val topLeft = Offset(cx + fr.dx * unit - side / 2f, cy + fr.dy * unit - side / 2f)
                        rotate(fr.rotation, pivot = Offset(cx + fr.dx * unit, cy + fr.dy * unit)) {
                            drawRect(b.color.toColor(), topLeft, Size(side, side), alpha = fr.alpha)
                            drawRect(Color(0xFF2B1B12), topLeft, Size(side, side), alpha = fr.alpha, style = androidx.compose.ui.graphics.drawscope.Stroke(width = side * 0.15f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultView(summary: WallBreakSummary, canRetry: Boolean, onRetry: () -> Unit, onMenu: () -> Unit) {
    val r = summary.result
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (r.cleared) "クリア!" else "おしまい", style = MaterialTheme.typography.headlineMedium)
        Text("${r.difficulty.label()}   突破 ${r.wallsBroken} 枚(目標 ${r.difficulty.targetWalls})   スコア ${r.score}", style = MaterialTheme.typography.titleMedium)
        Text(
            "ルール: ${if (r.rule == RuleKind.INK) "文字色" else "単語"}   ミス ${r.mistakes} 回   ミス無効 ${r.nullifiedUsed} 回使用   最大 ${r.maxCombo} コンボ",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (summary.isNewHighScore) Text("ハイスコア更新!", color = MaterialTheme.colorScheme.primary)
        if (summary.isFirstClear) Text("この難易度を初めてクリア!", color = MaterialTheme.colorScheme.primary)
        Text(
            if (summary.applied) {
                "育成に反映: 筋力 +${summary.gains.strength.toInt()} / 機嫌 +${summary.gains.mood.toInt()}"
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
