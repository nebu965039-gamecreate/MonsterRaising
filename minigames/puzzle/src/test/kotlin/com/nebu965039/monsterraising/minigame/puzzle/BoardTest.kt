package com.nebu965039.monsterraising.minigame.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {
    private fun row(vararg xs: Int) = xs.map { Cell(it, 0) }

    private fun Board.fillRow(y: Int) = place(row(*IntArray(cols) { it }), 0, y, PieceType.I)

    @Test
    fun canPlace_respectsTheEdges() {
        val b = Board(8, 14)
        val cells = PieceType.I.cells(0)
        assertTrue(b.canPlace(cells, 0, 0))
        assertTrue(b.canPlace(cells, 3, 13))
        assertFalse(b.canPlace(cells, -1, 0))
        assertFalse(b.canPlace(cells, 4, 0)) // 右へはみ出す
        assertFalse(b.canPlace(cells, 0, 14)) // 下へはみ出す
        assertFalse(b.canPlace(cells, 0, -1))
    }

    @Test
    fun canPlace_rejectsOccupiedCells() {
        val b = Board(8, 14)
        b.place(row(2), 0, 13, PieceType.T)
        assertFalse(b.canPlace(PieceType.I.cells(0), 0, 13))
        assertTrue(b.canPlace(PieceType.I.cells(0), 3, 13))
    }

    @Test
    fun clearFullRows_removesOnlyFullRowsAndDropsTheRowsAbove() {
        val b = Board(8, 14)
        b.fillRow(13)
        b.place(row(1, 2), 0, 12, PieceType.L) // 隙間のある行(消えない)
        assertEquals(1, b.clearFullRows())
        assertNull(b[1, 12])
        assertEquals(PieceType.L, b[1, 13]) // 上の行が 1 段落ちる
        assertEquals(PieceType.L, b[2, 13])
        assertEquals(2, b.filledCount())
    }

    @Test
    fun clearFullRows_handlesSeveralRowsIncludingNonAdjacentOnes() {
        val b = Board(8, 14)
        b.fillRow(13)
        b.place(row(0), 0, 12, PieceType.P) // 間に隙間のある行
        b.fillRow(11)
        assertEquals(2, b.clearFullRows())
        assertEquals(PieceType.P, b[0, 13]) // 隙間のある行だけが残り、最下段に落ちる
        assertEquals(1, b.filledCount())
    }

    @Test
    fun clearFullRows_returnsZeroWhenNothingIsFull() {
        val b = Board(8, 14)
        b.place(row(0, 1, 2), 0, 13, PieceType.W)
        assertEquals(0, b.clearFullRows())
        assertEquals(3, b.filledCount())
    }

    @Test
    fun clearFullRows_canClearEveryRow() {
        val b = Board(8, 14)
        for (y in 0 until 14) b.fillRow(y)
        assertEquals(14, b.clearFullRows())
        assertEquals(0, b.filledCount())
    }
}
