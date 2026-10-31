package com.friends.ships.game

import kotlin.random.Random

/** Pure Kotlin rules engine. It has no Android dependencies and is deterministic when given a Random. */
object GameEngine {
    val fleet: List<ShipType> = listOf(
        ShipType.CARRIER,
        ShipType.BATTLESHIP,
        ShipType.CRUISER,
        ShipType.SUBMARINE,
        ShipType.DESTROYER
    )

    fun validateFleet(ships: List<ShipPlacement>, noTouching: Boolean): PlacementValidation {
        if (ships.size != fleet.size) return PlacementValidation(false, PlacementError.WRONG_FLEET)
        if (ships.groupBy { it.type }.any { it.value.size > 1 }) {
            return PlacementValidation(false, PlacementError.DUPLICATE_SHIP)
        }
        if (ships.map { it.type }.toSet() != fleet.toSet()) return PlacementValidation(false, PlacementError.WRONG_FLEET)
        val placed = mutableListOf<ShipPlacement>()
        for (ship in ships) {
            val result = canPlace(placed, ship, noTouching)
            if (!result.valid) return result
            placed += ship
        }
        return PlacementValidation(true)
    }

    fun canPlace(
        existing: List<ShipPlacement>,
        candidate: ShipPlacement,
        noTouching: Boolean
    ): PlacementValidation {
        val cells = candidate.cells
        if (cells.any { !it.isOnBoard() }) return PlacementValidation(false, PlacementError.OUT_OF_BOUNDS)
        val occupied = existing.flatMap { it.cells }.toSet()
        if (cells.any { it in occupied }) return PlacementValidation(false, PlacementError.OVERLAP)
        if (noTouching) {
            val neighbors = cells.flatMap { cell ->
                listOf(
                    Cell(cell.row - 1, cell.col), Cell(cell.row + 1, cell.col),
                    Cell(cell.row, cell.col - 1), Cell(cell.row, cell.col + 1),
                    Cell(cell.row - 1, cell.col - 1), Cell(cell.row - 1, cell.col + 1),
                    Cell(cell.row + 1, cell.col - 1), Cell(cell.row + 1, cell.col + 1)
                )
            }.toSet()
            if (neighbors.any { it in occupied }) return PlacementValidation(false, PlacementError.TOUCHING)
        }
        return PlacementValidation(true)
    }

    fun randomFleet(noTouching: Boolean = false, random: Random = Random.Default): List<ShipPlacement> {
        val ordered = fleet.sortedByDescending { it.length }
        val result = mutableListOf<ShipPlacement>()
        fun place(index: Int): Boolean {
            if (index == ordered.size) return true
            val type = ordered[index]
            val candidates = buildList {
                for (row in 0..9) for (col in 0..9) {
                    add(ShipPlacement(type, Cell(row, col), vertical = false))
                    add(ShipPlacement(type, Cell(row, col), vertical = true))
                }
            }.shuffled(random)
            for (candidate in candidates) {
                if (canPlace(result, candidate, noTouching).valid) {
                    result += candidate
                    if (place(index + 1)) return true
                    result.removeAt(result.lastIndex)
                }
            }
            return false
        }
        check(place(0)) { "Could not generate a legal fleet" }
        return result.sortedBy { fleet.indexOf(it.type) }
    }


    /** Completes a partially placed fleet while preserving valid ships already placed by the player. */
    fun completeFleet(
        existing: List<ShipPlacement>,
        noTouching: Boolean = false,
        random: Random = Random.Default
    ): List<ShipPlacement> {
        val usedTypes = existing.map { it.type }
        require(usedTypes.size == usedTypes.toSet().size) { "Duplicate ship type" }
        val validated = mutableListOf<ShipPlacement>()
        existing.forEach { ship ->
            require(canPlace(validated, ship, noTouching).valid) { "Existing placement is invalid" }
            validated += ship
        }
        val missing = fleet.filterNot { it in usedTypes }.sortedByDescending { it.length }
        fun place(index: Int): Boolean {
            if (index == missing.size) return true
            val type = missing[index]
            val candidates = buildList {
                for (row in 0..9) for (col in 0..9) {
                    add(ShipPlacement(type, Cell(row, col), vertical = false))
                    add(ShipPlacement(type, Cell(row, col), vertical = true))
                }
            }.shuffled(random)
            for (candidate in candidates) {
                if (canPlace(validated, candidate, noTouching).valid) {
                    validated += candidate
                    if (place(index + 1)) return true
                    validated.removeAt(validated.lastIndex)
                }
            }
            return false
        }
        check(place(0)) { "Could not complete the fleet from the existing placements" }
        return validated.sortedBy { fleet.indexOf(it.type) }
    }

    fun resolveShot(
        ships: List<ShipPlacement>,
        triedCells: Set<Cell>,
        target: Cell,
        previouslyHitCells: Set<Cell> = triedCells
    ): ShotResolution {
        require(target.isOnBoard()) { "Shot is outside the board" }
        require(target !in triedCells) { "Cell has already been targeted" }
        val ship = ships.firstOrNull { target in it.cells }
            ?: return ShotResolution(ShotKind.MISS)
        val allHits = previouslyHitCells + target
        return if (ship.cells.all { it in allHits }) ShotResolution(ShotKind.SUNK, ship)
        else ShotResolution(ShotKind.HIT)
    }

    fun isFleetSunk(ships: List<ShipPlacement>, hitCells: Set<Cell>): Boolean =
        ships.isNotEmpty() && ships.all { ship -> ship.cells.all { it in hitCells } }

    fun nextAlive(order: List<String>, currentId: String, aliveIds: Set<String>): String? {
        if (aliveIds.isEmpty() || order.isEmpty()) return null
        val start = order.indexOf(currentId).let { if (it < 0) -1 else it }
        for (step in 1..order.size) {
            val candidate = order[(start + step).mod(order.size)]
            if (candidate in aliveIds) return candidate
        }
        return currentId.takeIf { it in aliveIds }
    }

    fun winner(aliveIds: Set<String>): String? = aliveIds.singleOrNull()
}
