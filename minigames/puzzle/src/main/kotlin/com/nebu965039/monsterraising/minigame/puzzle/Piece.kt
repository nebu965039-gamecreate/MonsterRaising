package com.nebu965039.monsterraising.minigame.puzzle

data class Cell(val x: Int, val y: Int)

/** ブロックの配色(基本設計書5.2.1節)。実際の色の値は UI 側で決める。 */
enum class PieceColor { ORANGE, BLUE, GREEN, RED, PURPLE, YELLOW, TEAL }

/**
 * オリジナルの 5 マス構成(ペントミノ)7 種(基本設計書5.2.1節)。
 * 形は設計書の表のとおり(`#` がマス)。回転パターンは 90 度ずつの回転から重複を除いて求める。
 */
enum class PieceType(val color: PieceColor, vararg pattern: String) {
    /** I型(直線) */
    I(PieceColor.ORANGE, "#####"),

    /** L型 */
    L(PieceColor.BLUE, "#....", "#....", "#....", "##..."),

    /** P型(かたまり) */
    P(PieceColor.GREEN, "##...", "##...", "#...."),

    /** T型 */
    T(PieceColor.RED, "###..", ".#...", ".#..."),

    /** Z型(階段) */
    Z(PieceColor.PURPLE, ".##..", ".#...", "##..."),

    /** W型(3段階段) */
    W(PieceColor.YELLOW, "#....", "##...", ".##.."),

    /** X型(十字) */
    X(PieceColor.TEAL, ".#...", "###..", ".#...");

    /** 回転 0 の形。座標は左上を (0,0) とし、x は右、y は下 */
    val baseCells: List<Cell> = pattern.flatMapIndexed { y, row ->
        row.mapIndexedNotNull { x, c -> if (c == '#') Cell(x, y) else null }
    }

    /** 90 度ずつ(時計回り)回転した形。対称な形は重複を除くため、X は 1 通り、I は 2 通りになる */
    val rotations: List<List<Cell>> = buildRotations(baseCells)

    /** [rotation] 番目(範囲外は周回)の形 */
    fun cells(rotation: Int): List<Cell> = rotations[Math.floorMod(rotation, rotations.size)]

}

private fun buildRotations(base: List<Cell>): List<List<Cell>> {
    val result = mutableListOf<List<Cell>>()
    var current = normalize(base)
    repeat(4) {
        if (result.none { it.toSet() == current.toSet() }) result += current
        // 画面座標(y が下向き)で (x, y) → (-y, x) は時計回りに 90 度
        current = normalize(current.map { Cell(-it.y, it.x) })
    }
    return result
}

private fun normalize(cells: List<Cell>): List<Cell> {
    val minX = cells.minOf { it.x }
    val minY = cells.minOf { it.y }
    return cells.map { Cell(it.x - minX, it.y - minY) }.sortedWith(compareBy({ it.y }, { it.x }))
}
