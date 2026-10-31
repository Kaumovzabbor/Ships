package com.friends.ships.game

import kotlin.random.Random

/** Lightweight hunt/target opponent. It never selects a cell twice. */
class HuntTargetBot(private val random: Random = Random.Default) {
    private val tried = mutableSetOf<Cell>()
    private val targets = ArrayDeque<Cell>()

    fun nextShot(): Cell {
        while (targets.isNotEmpty()) {
            val next = targets.removeFirst()
            if (next.isOnBoard() && next !in tried) return next
        }
        val parityCells = (0..9).flatMap { row -> (0..9).map { col -> Cell(row, col) } }
            .filter { (it.row + it.col) % 2 == 0 && it !in tried }
        val pool = parityCells.ifEmpty {
            (0..9).flatMap { row -> (0..9).map { col -> Cell(row, col) } }.filter { it !in tried }
        }
        check(pool.isNotEmpty()) { "No untried cells remain" }
        return pool[random.nextInt(pool.size)]
    }

    fun recordShot(cell: Cell, kind: ShotKind) {
        tried += cell
        if (kind == ShotKind.HIT) {
            listOf(
                Cell(cell.row - 1, cell.col), Cell(cell.row + 1, cell.col),
                Cell(cell.row, cell.col - 1), Cell(cell.row, cell.col + 1)
            ).filter { it.isOnBoard() && it !in tried && it !in targets }
                .shuffled(random)
                .forEach { targets.add(it) }
        }
        if (kind == ShotKind.SUNK) targets.clear()
    }

    fun triedCells(): Set<Cell> = tried.toSet()
}
