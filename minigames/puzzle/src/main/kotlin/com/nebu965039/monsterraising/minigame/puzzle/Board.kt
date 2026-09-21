package com.nebu965039.monsterraising.minigame.puzzle

/** 盤面。左上が (0,0)、x は右、y は下。 */
class Board(val cols: Int, val rows: Int) {
    private val grid = Array(rows) { arrayOfNulls<PieceType>(cols) }

    operator fun get(x: Int, y: Int): PieceType? = grid[y][x]

    /** 盤面の内側で、かつ空いているマスにだけ置ける([originX], [originY] は形の左上の位置) */
    fun canPlace(cells: List<Cell>, originX: Int, originY: Int): Boolean = cells.all {
        val x = originX + it.x
        val y = originY + it.y
        x in 0 until cols && y in 0 until rows && grid[y][x] == null
    }

    fun place(cells: List<Cell>, originX: Int, originY: Int, type: PieceType) {
        for (c in cells) grid[originY + c.y][originX + c.x] = type
    }

    /** 全マスが埋まった行を消し、上の行を詰める。消した行数を返す */
    fun clearFullRows(): Int {
        var cleared = 0
        var writeY = rows - 1
        for (readY in rows - 1 downTo 0) {
            if (grid[readY].all { it != null }) {
                cleared++
            } else {
                if (writeY != readY) grid[writeY] = grid[readY]
                writeY--
            }
        }
        // 詰めた分だけ、上に空の行を作る
        for (y in writeY downTo 0) grid[y] = arrayOfNulls(cols)
        return cleared
    }

    fun filledCount(): Int = grid.sumOf { row -> row.count { it != null } }
}
