package com.nebu965039.monsterraising.minigame.puzzle

/** 操作中のブロック。[x]・[y] は形の左上の位置。 */
data class ActivePiece(val type: PieceType, val rotation: Int, val x: Int, val y: Int) {
    val cells: List<Cell> get() = type.cells(rotation)
}

enum class GameStatus {
    Playing,

    /** 制限時間(90 秒)に達して終了 */
    TimeUp,

    /** 積み上がって次のブロックを出せず終了(暫定の扱い。スコアはそのまま結果になる) */
    ToppedOut,
}

/** 1 プレイの結果。育成への反映(有効度・機嫌)は呼び出し側で行う(5.4節)。 */
data class PuzzleResult(
    val difficulty: Difficulty,
    val score: Int,
    val linesCleared: Int,
    /** 目標スコアに到達したか(クリア判定。5.2節) */
    val cleared: Boolean,
    val endReason: GameStatus,
)

/**
 * 落ち物パズルの 1 プレイ(基本設計書5節)。時間は [tick] で外から進めるので、UI・テストのどちらからも同じ結果になる。
 * 操作([moveLeft]・[moveRight]・[rotate]・[softDrop]・[hardDrop])は、終了後は何もしない。
 */
class PuzzleGame(
    val difficulty: Difficulty,
    val config: PuzzleConfig = PuzzleConfig(),
    private val pieceSource: PieceSource = PieceBag(),
) {
    val board = Board(config.cols, config.rows)

    var current: ActivePiece
        private set
    var next: PieceType
        private set
    var status: GameStatus = GameStatus.Playing
        private set
    var score: Int = 0
        private set
    var linesCleared: Int = 0
        private set

    /** 今の連続消去の回数(ライン消去が起きなかったブロックで 0 に戻る) */
    var combo: Int = 0
        private set
    var elapsedMs: Long = 0L
        private set

    /** 目標スコアに到達した時刻(未到達なら null)。到達後も制限時間まで続けてよい */
    var clearedAtMs: Long? = null
        private set

    private var gravityAccumMs = 0L

    init {
        val first = pieceSource.next()
        next = pieceSource.next()
        current = spawnOf(first)
        // 最初のブロックが出せない盤面はありえないが、設定次第で起きうるので同じ扱いにする
        if (!fits(current)) status = GameStatus.ToppedOut
    }

    val timeLeftMs: Long get() = (config.timeLimitMs - elapsedMs).coerceAtLeast(0L)

    val isFinished: Boolean get() = status != GameStatus.Playing

    /** 操作中のブロックが、今の位置から真下に落ちて止まる位置(着地予告の表示用) */
    fun ghost(): ActivePiece {
        var p = current
        while (fits(p.copy(y = p.y + 1))) p = p.copy(y = p.y + 1)
        return p
    }

    /** 時間を [dtMs] 進める。落下の間隔は経過時間に応じて段階的に短くなる(5.2節)。 */
    fun tick(dtMs: Long) {
        if (isFinished || dtMs <= 0) return
        var remaining = minOf(dtMs, timeLeftMs)
        while (remaining > 0 && status == GameStatus.Playing) {
            val interval = config.fallIntervalMs(elapsedMs)
            // 落下の間隔が切り替わる境界をまたがないよう、次に落ちるまでの時間で区切って進める
            // (間隔が短くなった直後は、すでに溜まった分が新しい間隔を超えていることがあるので 0 を下限にする)
            val step = minOf(remaining, (interval - gravityAccumMs).coerceAtLeast(0L))
            elapsedMs += step
            gravityAccumMs += step
            remaining -= step
            if (gravityAccumMs >= interval) {
                gravityAccumMs = 0
                stepDown()
            }
        }
        if (status == GameStatus.Playing && elapsedMs >= config.timeLimitMs) status = GameStatus.TimeUp
    }

    fun moveLeft() = move(-1)

    fun moveRight() = move(1)

    /** 時計回りに回転する。壁・ブロックに当たる場合は左右に少しずらして入れる */
    fun rotate() {
        if (isFinished) return
        val turned = current.copy(rotation = current.rotation + 1)
        for (dx in KICKS) {
            val candidate = turned.copy(x = turned.x + dx)
            if (fits(candidate)) {
                current = candidate
                return
            }
        }
    }

    /** 1 マス下げる。下がれなければその場に固定する */
    fun softDrop() {
        if (isFinished) return
        stepDown()
        gravityAccumMs = 0
    }

    /** 一番下まで落として固定する */
    fun hardDrop() {
        if (isFinished) return
        current = ghost()
        lock()
    }

    fun result() = PuzzleResult(difficulty, score, linesCleared, cleared = clearedAtMs != null, endReason = status)

    private fun move(dx: Int) {
        if (isFinished) return
        val moved = current.copy(x = current.x + dx)
        if (fits(moved)) current = moved
    }

    private fun stepDown() {
        val moved = current.copy(y = current.y + 1)
        if (fits(moved)) current = moved else lock()
    }

    private fun lock() {
        board.place(current.cells, current.x, current.y, current.type)
        val lines = board.clearFullRows()
        if (lines > 0) {
            combo++
            linesCleared += lines
            score += config.lineScore(lines, combo)
            if (clearedAtMs == null && score >= difficulty.targetScore) clearedAtMs = elapsedMs
        } else {
            combo = 0
        }
        val spawned = spawnOf(next)
        next = pieceSource.next()
        current = spawned
        gravityAccumMs = 0
        if (!fits(spawned)) status = GameStatus.ToppedOut
    }

    private fun spawnOf(type: PieceType): ActivePiece {
        val width = type.cells(0).maxOf { it.x } + 1
        return ActivePiece(type, rotation = 0, x = (config.cols - width) / 2, y = 0)
    }

    private fun fits(p: ActivePiece) = board.canPlace(p.cells, p.x, p.y)

    private companion object {
        /** 回転できないときに試す左右のずらし量(なるべく小さいずらしを先に) */
        val KICKS = listOf(0, -1, 1, -2, 2)
    }
}
