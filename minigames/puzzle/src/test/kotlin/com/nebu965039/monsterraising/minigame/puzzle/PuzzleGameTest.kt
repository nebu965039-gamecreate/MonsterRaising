package com.nebu965039.monsterraising.minigame.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PuzzleGameTest {
    /** 出す順番を決め打ちにする。使い切ったら最後の形を出し続ける */
    private class Script(vararg types: PieceType) : PieceSource {
        private val queue = ArrayDeque(types.toList())
        private var last = types.last()

        override fun next(): PieceType = if (queue.isEmpty()) last else queue.removeFirst().also { last = it }
    }

    private fun game(
        vararg pieces: PieceType,
        difficulty: Difficulty = Difficulty.BEGINNER,
        config: PuzzleConfig = PuzzleConfig(),
    ) = PuzzleGame(difficulty, config, Script(*pieces))

    /** 下 2 行の左 3 マス(x=0..2)を埋めておく。残りの 5 マスは I 型(横)で埋まる */
    private fun PuzzleGame.prefillLeftThree(vararg rows: Int) {
        for (y in rows) board.place(listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0)), 0, y, PieceType.L)
    }

    private fun PuzzleGame.dropIAtRightSide() {
        // I 型(横 5 マス)は x=1 から出る。右端(x=3..7)へ寄せて落とす
        repeat(2) { moveRight() }
        hardDrop()
    }

    /** 積み上がって途中で終わらないよう、盤面を高くして時間切れだけを確かめるための設定 */
    private val tallBoard = PuzzleConfig(rows = 1_000)

    // --- 出現・移動 ---

    @Test
    fun startsPlayingWithTheFirstPieceAtTheTopCenter() {
        val g = game(PieceType.I, PieceType.L)
        assertEquals(GameStatus.Playing, g.status)
        assertEquals(PieceType.I, g.current.type)
        assertEquals(PieceType.L, g.next)
        assertEquals(0, g.current.y)
        assertEquals(1, g.current.x) // (8 - 5) / 2
        assertEquals(90_000L, g.timeLeftMs)
    }

    @Test
    fun moves_stopAtTheWalls() {
        val g = game(PieceType.I, PieceType.L)
        repeat(20) { g.moveLeft() }
        assertEquals(0, g.current.x)
        repeat(20) { g.moveRight() }
        assertEquals(3, g.current.x) // 横 5 マスなので右端は x=3
    }

    @Test
    fun rotate_switchesTheShape() {
        val g = game(PieceType.I, PieceType.L)
        g.rotate()
        assertEquals(1, g.current.cells.maxOf { it.x } + 1) // 縦になる
        g.rotate()
        assertEquals(5, g.current.cells.maxOf { it.x } + 1)
    }

    @Test
    fun rotate_kicksAwayFromTheWall() {
        val g = game(PieceType.L, PieceType.I)
        repeat(20) { g.moveRight() }
        assertEquals(6, g.current.x) // L 型(横 2 マス)の右端
        g.rotate() // 横 4 マスになるので、そのままでは右へはみ出す → 左へずらして入る
        assertEquals(1, g.current.rotation)
        assertEquals(4, g.current.x)
    }

    @Test
    fun rotate_isRefusedWhenNoKickFits() {
        val g = game(PieceType.I, PieceType.L)
        g.rotate() // 縦
        repeat(20) { g.moveRight() }
        assertEquals(7, g.current.x)
        g.rotate() // 横 5 マスは右壁ではずらしても入らない
        assertEquals(1, g.current.rotation)
        assertEquals(7, g.current.x)
    }

    // --- 落下・固定 ---

    @Test
    fun gravity_dropsOneRowPerInterval() {
        val g = game(PieceType.I, PieceType.L)
        g.tick(999)
        assertEquals(0, g.current.y)
        g.tick(1)
        assertEquals(1, g.current.y)
        g.tick(2_000)
        assertEquals(3, g.current.y)
    }

    @Test
    fun softDrop_movesOneRow() {
        val g = game(PieceType.I, PieceType.L)
        g.softDrop()
        assertEquals(1, g.current.y)
    }

    @Test
    fun hardDrop_locksAtTheBottomAndSpawnsTheNextPiece() {
        val g = game(PieceType.I, PieceType.L, PieceType.T)
        g.hardDrop()
        assertEquals(5, g.board.filledCount())
        for (x in 1..5) assertEquals(PieceType.I, g.board[x, 13])
        assertEquals(PieceType.L, g.current.type)
        assertEquals(PieceType.T, g.next)
        assertEquals(0, g.current.y)
    }

    @Test
    fun ghost_showsWhereThePieceWillLand() {
        val g = game(PieceType.I, PieceType.L)
        assertEquals(13, g.ghost().y)
        g.hardDrop()
        // 次の L 型(縦 4 マス)は I 型の上に乗る位置か、横にずれて底に着く
        val landing = g.ghost()
        assertTrue(landing.y > 0)
    }

    @Test
    fun locking_stacksOnTopOfExistingBlocks() {
        val g = game(PieceType.I, PieceType.I, PieceType.L)
        g.hardDrop()
        g.hardDrop()
        for (x in 1..5) assertEquals(PieceType.I, g.board[x, 12])
    }

    // --- 得点(5.3) ---

    @Test
    fun oneLine_scoresOneHundred() {
        val g = game(PieceType.I, PieceType.I, PieceType.I)
        g.prefillLeftThree(13)
        g.dropIAtRightSide()
        assertEquals(100, g.score)
        assertEquals(1, g.linesCleared)
        assertEquals(1, g.combo)
        assertEquals(0, g.board.filledCount() - 0) // 消えた行の分は残らない
    }

    @Test
    fun consecutiveLines_getTheComboBonus() {
        val g = game(PieceType.I, PieceType.I, PieceType.I, PieceType.I)
        g.prefillLeftThree(13, 12)
        g.dropIAtRightSide()
        assertEquals(100, g.score)
        g.dropIAtRightSide()
        assertEquals(225, g.score) // 2 回目は 125 点(×1.25)
        assertEquals(2, g.combo)
        assertEquals(2, g.linesCleared)
    }

    @Test
    fun combo_resetsWhenAPieceClearsNothing() {
        val g = game(PieceType.I, PieceType.I, PieceType.T, PieceType.L)
        g.prefillLeftThree(13)
        g.dropIAtRightSide()
        assertEquals(1, g.combo)
        g.hardDrop() // 2 つ目の I 型は何も消さない
        assertEquals(0, g.combo)
    }

    @Test
    fun severalLinesAtOnce_useTheMultiLineMultiplier() {
        // 縦向きの I 型で 2 行同時に消す: 右端の 1 列を空けておき、縦 I を落とす
        val g = game(PieceType.I, PieceType.L)
        for (y in 12..13) g.board.place((0..6).map { Cell(it, 0) }, 0, y, PieceType.L)
        g.rotate() // 縦
        repeat(20) { g.moveRight() } // x=7
        g.hardDrop()
        assertEquals(config().lineScore(2, 1), g.score) // 100 × 2 × 1.5 = 300
        assertEquals(2, g.linesCleared)
    }

    private fun config() = PuzzleConfig()

    // --- クリア判定(5.2) ---

    @Test
    fun clear_isLatchedWhenTheTargetScoreIsReached() {
        val g = game(PieceType.I, PieceType.I, PieceType.I, PieceType.I, PieceType.I, difficulty = Difficulty.BEGINNER)
        g.prefillLeftThree(13, 12, 11)
        g.dropIAtRightSide()
        g.dropIAtRightSide()
        assertNull(g.clearedAtMs) // 225 点
        assertFalse(g.result().cleared)
        g.tick(3_000)
        g.dropIAtRightSide() // 100 + 125 + 150 = 375 ≥ 300
        assertEquals(375, g.score)
        assertNotNull(g.clearedAtMs)
        assertTrue(g.result().cleared)
        assertEquals(GameStatus.Playing, g.status) // 目標に届いても、制限時間までは続けられる
    }

    @Test
    fun targetScores_matchTheDesign() {
        assertEquals(300, Difficulty.BEGINNER.targetScore)
        assertEquals(800, Difficulty.INTERMEDIATE.targetScore)
        assertEquals(1500, Difficulty.ADVANCED.targetScore)
    }

    // --- 終了 ---

    @Test
    fun timeUp_endsTheGameAtNinetySeconds() {
        val g = game(PieceType.X, PieceType.X, PieceType.X, config = tallBoard)
        g.tick(89_999)
        assertEquals(GameStatus.Playing, g.status)
        g.tick(10)
        assertEquals(GameStatus.TimeUp, g.status)
        assertEquals(90_000L, g.elapsedMs)
        assertEquals(0L, g.timeLeftMs)
    }

    @Test
    fun afterTheEnd_inputsAreIgnored() {
        val g = game(PieceType.I, PieceType.L, config = tallBoard)
        g.tick(200_000)
        val before = g.current
        g.moveLeft()
        g.rotate()
        g.softDrop()
        g.hardDrop()
        g.tick(1_000)
        assertEquals(before, g.current)
        assertEquals(90_000L, g.elapsedMs)
    }

    @Test
    fun toppedOut_whenTheNextPieceCannotSpawn() {
        val g = game(PieceType.I, PieceType.L, PieceType.L)
        // 出現位置(上 2 行の中央付近)をふさいでおく
        g.board.place(listOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1)), 3, 0, PieceType.P)
        g.hardDrop()
        assertEquals(GameStatus.ToppedOut, g.status)
        assertEquals(GameStatus.ToppedOut, g.result().endReason)
    }

    @Test
    fun result_carriesScoreLinesAndDifficulty() {
        val g = game(PieceType.I, PieceType.I, difficulty = Difficulty.INTERMEDIATE)
        g.prefillLeftThree(13)
        g.dropIAtRightSide()
        val r = g.result()
        assertEquals(Difficulty.INTERMEDIATE, r.difficulty)
        assertEquals(100, r.score)
        assertEquals(1, r.linesCleared)
        assertFalse(r.cleared) // 中級の目標は 800
    }

    // --- 落下速度(5.2) ---

    @Test
    fun fallSpeed_risesInStages() {
        val c = PuzzleConfig()
        assertEquals(1_000L, c.fallIntervalMs(0))
        assertEquals(1_000L, c.fallIntervalMs(14_999))
        assertEquals(800L, c.fallIntervalMs(15_000))
        assertEquals(640L, c.fallIntervalMs(30_000))
        assertTrue(c.fallIntervalMs(90_000) < c.fallIntervalMs(45_000))
        assertEquals(150L, c.fallIntervalMs(10_000_000)) // 下限
    }

    @Test
    fun ticking_acrossASpeedUpBoundaryNeverRewindsTime() {
        val g = game(PieceType.X, PieceType.X, PieceType.X, config = tallBoard)
        var last = 0L
        repeat(2_000) {
            g.tick(50)
            assertTrue(g.elapsedMs >= last)
            last = g.elapsedMs
        }
    }

    // --- 得点の式 ---

    @Test
    fun lineScoreFormula() {
        val c = PuzzleConfig()
        assertEquals(100, c.lineScore(1, 1))
        assertEquals(300, c.lineScore(2, 1))
        assertEquals(600, c.lineScore(3, 1))
        assertEquals(1_200, c.lineScore(4, 1))
        assertEquals(2_000, c.lineScore(5, 1))
        assertEquals(2_400, c.lineScore(6, 1)) // 倍率は 5 行以上で頭打ち
        assertEquals(125, c.lineScore(1, 2))
        assertEquals(150, c.lineScore(1, 3))
    }

    // --- 総当たり(不変条件) ---

    @Test
    fun randomPlay_keepsTheBoardConsistent() {
        for (seed in 1..40) {
            val rnd = Random(seed)
            val g = PuzzleGame(Difficulty.ADVANCED, PuzzleConfig(), PieceBag(Random(seed)))
            var lastScore = 0
            var steps = 0
            while (!g.isFinished && steps < 4_000) {
                when (rnd.nextInt(7)) {
                    0 -> g.moveLeft()
                    1 -> g.moveRight()
                    2 -> g.rotate()
                    3 -> g.softDrop()
                    4 -> g.hardDrop()
                    else -> g.tick(rnd.nextLong(1, 400))
                }
                // ブロックは 1 つにつき 5 マス足され、消えるのは 1 行 8 マス
                assertEquals(0, (g.board.filledCount() + 8 * g.linesCleared) % 5)
                assertTrue(g.score >= lastScore)
                assertTrue(g.elapsedMs in 0..90_000)
                lastScore = g.score
                steps++
            }
        }
    }
}
