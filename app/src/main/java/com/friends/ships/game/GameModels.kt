package com.friends.ships.game

import kotlinx.serialization.Serializable

@Serializable
data class Cell(val row: Int, val col: Int) {
    fun isOnBoard(): Boolean = row in 0..9 && col in 0..9
    override fun toString(): String = "${('A'.code + col).toChar()}${row + 1}"
}

@Serializable
enum class ShipType(val length: Int, val stringKey: String) {
    CARRIER(5, "fleet_carrier"),
    BATTLESHIP(4, "fleet_battleship"),
    CRUISER(3, "fleet_cruiser"),
    SUBMARINE(3, "fleet_submarine"),
    DESTROYER(2, "fleet_destroyer")
}

@Serializable
data class ShipPlacement(
    val type: ShipType,
    val start: Cell,
    val vertical: Boolean
) {
    val cells: List<Cell>
        get() = (0 until type.length).map { offset ->
            if (vertical) Cell(start.row + offset, start.col) else Cell(start.row, start.col + offset)
        }
}

@Serializable
enum class ShotKind { MISS, HIT, SUNK }

@Serializable
data class ShotMark(val cell: Cell, val kind: ShotKind)

data class ShotResolution(
    val kind: ShotKind,
    val sunkShip: ShipPlacement? = null
)

data class PlacementValidation(val valid: Boolean, val reason: PlacementError? = null)

enum class PlacementError { OUT_OF_BOUNDS, OVERLAP, TOUCHING, WRONG_FLEET, DUPLICATE_SHIP }
