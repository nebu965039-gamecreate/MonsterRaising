package com.nebu965039.monsterraising.ui.puzzle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nebu965039.monsterraising.core.minigame.ClearRewards
import com.nebu965039.monsterraising.core.minigame.DayClock
import com.nebu965039.monsterraising.core.minigame.ItemType
import com.nebu965039.monsterraising.core.minigame.MiniGame
import com.nebu965039.monsterraising.core.minigame.MiniGameProgress
import com.nebu965039.monsterraising.core.minigame.MinigameGains
import com.nebu965039.monsterraising.core.minigame.PuzzleRecords
import com.nebu965039.monsterraising.core.minigame.PuzzleRewards
import com.nebu965039.monsterraising.core.minigame.RewardTier
import com.nebu965039.monsterraising.core.pet.PetSimulator
import com.nebu965039.monsterraising.core.pet.Stage
import com.nebu965039.monsterraising.data.MiniGameStore
import com.nebu965039.monsterraising.data.PetStore
import com.nebu965039.monsterraising.data.careConfig
import com.nebu965039.monsterraising.data.PuzzleRecordStore
import com.nebu965039.monsterraising.minigame.puzzle.Cell
import com.nebu965039.monsterraising.minigame.puzzle.Difficulty
import com.nebu965039.monsterraising.minigame.puzzle.GameStatus
import com.nebu965039.monsterraising.minigame.puzzle.PieceColor
import com.nebu965039.monsterraising.minigame.puzzle.PieceType
import com.nebu965039.monsterraising.minigame.puzzle.PuzzleGame
import com.nebu965039.monsterraising.minigame.puzzle.PuzzleResult
import com.nebu965039.monsterraising.ui.common.BackgroundImages
import com.nebu965039.monsterraising.ui.common.GAME_CENTER_BACKGROUND_PATH
import com.nebu965039.monsterraising.ui.common.ScreenBackground
import com.nebu965039.monsterraising.ui.demo.DemoClock
import com.nebu965039.monsterraising.ui.minigame.PlayLimitPanel
import com.nebu965039.monsterraising.ui.minigame.rewardsText
import com.nebu965039.monsterraising.widget.PetWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 1 プレイが終わったあとに表示する内容。 */
private data class PlaySummary(
    val result: PuzzleResult,
    val gains: MinigameGains,
    /** 育成へ反映できたか(死亡・卵の場合は反映されない) */
    val applied: Boolean,
    val isNewHighScore: Boolean,
    val isFirstClear: Boolean,
    /** 有効度が 1 日の上限で削られた */
    val capped: Boolean,
    val rewards: ClearRewards?,
)

private fun Difficulty.label() = when (this) {
    Difficulty.BEGINNER -> "初級"
    Difficulty.INTERMEDIATE -> "中級"
    Difficulty.ADVANCED -> "上級"
}

/** 落ち物パズル(基本設計書5節)の画面。難易度を選び、90 秒のタイムアタックを遊ぶ。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PuzzleScreen() {
    val context = LocalContext.current
    val petStore = remember { PetStore(context) }
    val recordStore = remember { PuzzleRecordStore(context) }
    val progressStore = remember { MiniGameStore(context) }
    var progress by remember { mutableStateOf(progressStore.load()) }
    fun day() = DayClock.dayIndex(DemoClock.now())
    var difficulty by remember { mutableStateOf(Difficulty.BEGINNER) }
    var game by remember { mutableStateOf<PuzzleGame?>(null) }
    var records by remember { mutableStateOf(recordStore.load()) }
    var summary by remember { mutableStateOf<PlaySummary?>(null) }
    val isEgg = petStore.load()?.stage == Stage.EGG

    /** プレイ回数を 1 つ使って始める。上限に達していれば始めない。 */
    fun startGame(target: Difficulty) {
        val started = progressStore.update { p ->
            val next = p.startPlay(MiniGame.PUZZLE, day())
            if (next == null) p to false else next to true
        }
        progress = progressStore.load()
        if (started) game = PuzzleGame(target)
    }

    fun finish(result: PuzzleResult) {
        val update = recordStore.record(result)
        records = update.records
        val settlement = progressStore.update { p ->
            val s = p.settle(
                MiniGame.PUZZLE, day(), RewardTier.valueOf(result.difficulty.name),
                cleared = result.cleared, firstClear = update.isFirstClear, gains = PuzzleRewards.gains(result),
            )
            s.progress to s
        }
        progress = settlement.progress
        val before = petStore.load()
        val config = careConfig(context)
        val after = petStore.update(DemoClock.now(), config) { PetSimulator.applyMinigame(it, DemoClock.now(), settlement.appliedGains, config) }
        summary = PlaySummary(
            result, settlement.appliedGains,
            applied = before != null && before.stage != Stage.EGG && after.generation == before.generation,
            isNewHighScore = update.isNewHighScore,
            isFirstClear = update.isFirstClear,
            capped = settlement.capped,
            rewards = settlement.rewards,
        )
        PetWidgetUpdater.updateAll(context)
    }

    // ゲームセンターの背景(任意。3本のミニゲーム共通。8.1節)。開始前の画面にだけ敷く
    val gameCenterBg by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) { BackgroundImages.load(context, GAME_CENTER_BACKGROUND_PATH) }
    }

    val current = game
    val result = summary
    when {
        current != null && result == null -> PlayView(current, onFinished = { finish(it) })
        result != null -> ResultView(
            result,
            onRetry = {
                summary = null
                difficulty = result.result.difficulty
                game = null
                startGame(difficulty)
            },
            onMenu = { summary = null; game = null },
            canRetry = progress.playStatus(MiniGame.PUZZLE, day()).remaining > 0,
        )
        else -> ScreenBackground(gameCenterBg, scrimAlpha = 0.82f) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Text("落ち物パズル", style = MaterialTheme.typography.titleLarge)
            Text(
                "制限時間 90 秒。ブロックを積んでラインを揃えて消し、目標スコアを目指します。" +
                    "結果は知力系・筋力系の有効度と機嫌に反映されます。",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    FilterChip(
                        selected = d == difficulty,
                        onClick = { difficulty = d },
                        label = { Text("${d.label()}(目標 ${d.targetScore})") },
                    )
                }
            }
            RecordText(records, difficulty)
            if (isEgg) {
                Text("卵の間は遊べません。", color = MaterialTheme.colorScheme.error)
            }
            PlayLimitPanel(
                status = progress.playStatus(MiniGame.PUZZLE, day()),
                progress = progress,
                startEnabled = !isEgg,
                onStart = { startGame(difficulty) },
                onWatchAd = {
                    // 広告は Phase 8 で実装する。それまではデモとして、広告なしで 1 回追加する
                    progressStore.update { p -> (p.addAdPlay(MiniGame.PUZZLE, day()) ?: p) to Unit }
                    progress = progressStore.load()
                },
                onUseTicket = {
                    progressStore.update { p -> (p.addTicketPlay(MiniGame.PUZZLE, day()) ?: p) to Unit }
                    progress = progressStore.load()
                },
            )
            }
        }
    }
}

@Composable
private fun RecordText(records: PuzzleRecords, difficulty: Difficulty) {
    val cleared = if (records.hasCleared(difficulty)) "クリア済み" else "未クリア"
    Text("ハイスコア ${records.highScore(difficulty)}   $cleared", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun PlayView(game: PuzzleGame, onFinished: (PuzzleResult) -> Unit) {
    // ゲームは画面の外の状態(Compose の状態ではない)なので、毎フレーム値を増やして再描画させる
    var frame by remember(game) { mutableIntStateOf(0) }
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(game) {
        var last = -1L
        while (!game.isFinished) {
            withFrameMillis { now ->
                // 画面が背面にあった間の空白を進めてしまわないよう、1 フレームで進める時間に上限を設ける
                if (last >= 0) game.tick(minOf(now - last, MAX_FRAME_MS))
                last = now
                frame++
            }
        }
        frame++
        finished(game.result())
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val f = frame // 読み取って再コンポーズさせる
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("スコア ${game.score} / ${game.difficulty.targetScore}", style = MaterialTheme.typography.titleMedium)
            Text("残り ${(game.timeLeftMs + 999) / 1000} 秒", style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${game.difficulty.label()}   ライン ${game.linesCleared}", style = MaterialTheme.typography.bodyMedium)
            Text(
                when {
                    game.clearedAtMs != null -> "CLEAR!"
                    game.combo >= 2 -> "${game.combo} コンボ"
                    else -> ""
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.aspectRatio(game.config.cols.toFloat() / game.config.rows).fillMaxSize()) {
                    if (f >= 0) drawBoard(game)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NEXT", style = MaterialTheme.typography.labelMedium)
                Canvas(Modifier.size(72.dp).background(BOARD_BACKGROUND)) { drawPreview(game.next) }
            }
        }
        Controls(game)
    }
}

@Composable
private fun Controls(game: PuzzleGame) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoldButton("◀", Modifier.weight(1f), repeat = true) { game.moveLeft() }
            HoldButton("⟳", Modifier.weight(1f), repeat = false) { game.rotate() }
            HoldButton("▶", Modifier.weight(1f), repeat = true) { game.moveRight() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoldButton("▼ ゆっくり", Modifier.weight(1f), repeat = true) { game.softDrop() }
            HoldButton("⤓ 一気に", Modifier.weight(1f), repeat = false) { game.hardDrop() }
        }
    }
}

/** 押している間、一定の間隔で繰り返し動作するボタン(左右・下への移動用)。 */
@Composable
private fun HoldButton(label: String, modifier: Modifier, repeat: Boolean, onAction: () -> Unit) {
    val scope = rememberCoroutineScope()
    val action by rememberUpdatedState(onAction)
    Box(
        modifier
            .height(56.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    action()
                    val job = if (repeat) {
                        scope.launch {
                            delay(REPEAT_DELAY_MS)
                            while (true) {
                                action()
                                delay(REPEAT_INTERVAL_MS)
                            }
                        }
                    } else {
                        null
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        job?.cancel()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ResultView(summary: PlaySummary, canRetry: Boolean, onRetry: () -> Unit, onMenu: () -> Unit) {
    val r = summary.result
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (r.cleared) "クリア!" else "おしまい", style = MaterialTheme.typography.headlineMedium)
        Text(
            when (r.endReason) {
                GameStatus.TimeUp -> "時間切れ"
                GameStatus.ToppedOut -> "積み上がってしまった"
                GameStatus.Playing -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("${r.difficulty.label()}   スコア ${r.score}(目標 ${r.difficulty.targetScore})", style = MaterialTheme.typography.titleMedium)
        Text("消したライン ${r.linesCleared}", style = MaterialTheme.typography.bodyMedium)
        if (summary.isNewHighScore) Text("ハイスコア更新!", color = MaterialTheme.colorScheme.primary)
        if (summary.isFirstClear) Text("この難易度を初めてクリア!", color = MaterialTheme.colorScheme.primary)
        Text(
            if (summary.applied) {
                "育成に反映: 知力 +${summary.gains.intellect.toInt()} / 筋力 +${summary.gains.strength.toInt()} / 機嫌 +${summary.gains.mood.toInt()}"
            } else {
                "育成には反映されませんでした(卵、または遊んでいる間に世代が替わりました)"
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (summary.capped) {
            Text("今日の有効度の上限に達したため、加算が一部(または全部)反映されませんでした", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        summary.rewards?.let { rewards ->
            Text("クリア報酬: ${rewardsText(rewards)}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = canRetry, onClick = onRetry) { Text("もう一度") }
            OutlinedButton(onClick = onMenu) { Text("メニューへ") }
        }
    }
}

// --- 描画 ---

private const val MAX_FRAME_MS = 100L
private const val REPEAT_DELAY_MS = 180L
private const val REPEAT_INTERVAL_MS = 70L
private val BOARD_BACKGROUND = Color(0xFF1F2A24)
private val GRID_LINE = Color(0xFF33443B)
private val OUTLINE = Color(0xFF2B1B12)

/** 配色(基本設計書5.2.1節)。太いアウトライン+フラットカラーで描く。 */
private fun PieceColor.toColor(): Color = when (this) {
    PieceColor.ORANGE -> Color(0xFFF59A2B)
    PieceColor.BLUE -> Color(0xFF3F7FE0)
    PieceColor.GREEN -> Color(0xFF4CAF50)
    PieceColor.RED -> Color(0xFFE04B4B)
    PieceColor.PURPLE -> Color(0xFF9C5FD0)
    PieceColor.YELLOW -> Color(0xFFF2D338)
    PieceColor.TEAL -> Color(0xFF25A6A0)
}

private fun DrawScope.drawBlock(x: Float, y: Float, size: Float, color: Color, alpha: Float = 1f) {
    drawRect(color, Offset(x, y), Size(size, size), alpha = alpha)
    drawRect(OUTLINE, Offset(x, y), Size(size, size), alpha = alpha, style = Stroke(width = size * 0.12f))
}

private fun DrawScope.drawBoard(game: PuzzleGame) {
    val cell = minOf(size.width / game.config.cols, size.height / game.config.rows)
    val left = (size.width - cell * game.config.cols) / 2f
    val top = (size.height - cell * game.config.rows) / 2f
    drawRect(BOARD_BACKGROUND, Offset(left, top), Size(cell * game.config.cols, cell * game.config.rows))
    for (x in 0..game.config.cols) {
        drawLine(GRID_LINE, Offset(left + x * cell, top), Offset(left + x * cell, top + cell * game.config.rows))
    }
    for (y in 0..game.config.rows) {
        drawLine(GRID_LINE, Offset(left, top + y * cell), Offset(left + cell * game.config.cols, top + y * cell))
    }
    for (y in 0 until game.config.rows) for (x in 0 until game.config.cols) {
        val type = game.board[x, y] ?: continue
        drawBlock(left + x * cell, top + y * cell, cell, type.color.toColor())
    }
    if (!game.isFinished) {
        val ghost = game.ghost()
        for (c in ghost.cells) drawBlock(left + (ghost.x + c.x) * cell, top + (ghost.y + c.y) * cell, cell, ghost.type.color.toColor(), alpha = 0.25f)
        val p = game.current
        for (c in p.cells) drawBlock(left + (p.x + c.x) * cell, top + (p.y + c.y) * cell, cell, p.type.color.toColor())
    }
}

private fun DrawScope.drawPreview(type: PieceType) {
    val cells: List<Cell> = type.cells(0)
    val w = cells.maxOf { it.x } + 1
    val h = cells.maxOf { it.y } + 1
    val cell = minOf(size.width / 5f, size.height / 5f)
    val left = (size.width - cell * w) / 2f
    val top = (size.height - cell * h) / 2f
    for (c in cells) drawBlock(left + c.x * cell, top + c.y * cell, cell, type.color.toColor())
}
