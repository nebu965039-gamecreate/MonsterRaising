package com.nebu965039.monsterraising.minigame.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PieceTest {
    @Test
    fun everyPieceHasFiveCells() {
        for (type in PieceType.entries) {
            assertEquals("$type base", 5, type.baseCells.size)
            for (r in type.rotations) assertEquals("$type rotation", 5, r.size)
        }
    }

    @Test
    fun shapesMatchTheDesignTable() {
        assertEquals(setOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0), Cell(4, 0)), PieceType.I.baseCells.toSet())
        assertEquals(setOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(0, 3), Cell(1, 3)), PieceType.L.baseCells.toSet())
        assertEquals(setOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1), Cell(0, 2)), PieceType.P.baseCells.toSet())
        assertEquals(setOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(1, 1), Cell(1, 2)), PieceType.T.baseCells.toSet())
        assertEquals(setOf(Cell(1, 0), Cell(2, 0), Cell(1, 1), Cell(0, 2), Cell(1, 2)), PieceType.Z.baseCells.toSet())
        assertEquals(setOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(1, 2), Cell(2, 2)), PieceType.W.baseCells.toSet())
        assertEquals(setOf(Cell(1, 0), Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(1, 2)), PieceType.X.baseCells.toSet())
    }

    @Test
    fun distinctRotationCounts() {
        assertEquals(2, PieceType.I.rotations.size)
        assertEquals(4, PieceType.L.rotations.size)
        assertEquals(4, PieceType.P.rotations.size)
        assertEquals(4, PieceType.T.rotations.size)
        assertEquals(2, PieceType.Z.rotations.size) // 点対称なので 180 度で元に戻る
        assertEquals(4, PieceType.W.rotations.size)
        assertEquals(1, PieceType.X.rotations.size)
    }

    @Test
    fun rotationsAreNormalizedToTheTopLeft() {
        for (type in PieceType.entries) for (r in type.rotations) {
            assertEquals(0, r.minOf { it.x })
            assertEquals(0, r.minOf { it.y })
        }
    }

    @Test
    fun rotationIndexWrapsAround() {
        for (type in PieceType.entries) {
            assertEquals(type.cells(0), type.cells(type.rotations.size))
            assertEquals(type.cells(1), type.cells(1 + type.rotations.size * 3))
            assertEquals(type.cells(type.rotations.size - 1), type.cells(-1))
        }
    }

    @Test
    fun verticalIStandsFiveTall() {
        val vertical = PieceType.I.cells(1)
        assertEquals(1, vertical.maxOf { it.x } + 1)
        assertEquals(5, vertical.maxOf { it.y } + 1)
    }

    @Test
    fun colorsAreAllDifferentAndFollowTheDesign() {
        assertEquals(7, PieceType.entries.map { it.color }.toSet().size)
        assertEquals(PieceColor.ORANGE, PieceType.I.color)
        assertEquals(PieceColor.BLUE, PieceType.L.color)
        assertEquals(PieceColor.GREEN, PieceType.P.color)
        assertEquals(PieceColor.RED, PieceType.T.color)
        assertEquals(PieceColor.PURPLE, PieceType.Z.color)
        assertEquals(PieceColor.YELLOW, PieceType.W.color)
        assertEquals(PieceColor.TEAL, PieceType.X.color)
    }

    @Test
    fun bag_dealsEveryTypeOncePerRound() {
        val bag = PieceBag(Random(7))
        repeat(3) {
            val round = List(7) { bag.next() }
            assertEquals(PieceType.entries.toSet(), round.toSet())
        }
    }

    @Test
    fun bag_isReproducibleWithTheSameSeed() {
        val a = PieceBag(Random(123))
        val b = PieceBag(Random(123))
        assertEquals(List(20) { a.next() }, List(20) { b.next() })
        assertTrue(List(20) { PieceBag(Random(1)).next() }.isNotEmpty())
    }
}
