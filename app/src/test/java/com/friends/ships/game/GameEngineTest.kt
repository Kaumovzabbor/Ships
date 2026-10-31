package com.friends.ships.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    @Test fun placementRejectsOutOfBounds() {
        val carrier = ShipPlacement(ShipType.CARRIER, Cell(0, 7), vertical = false)
        assertEquals(PlacementError.OUT_OF_BOUNDS, GameEngine.canPlace(emptyList(), carrier, false).reason)
    }

    @Test fun placementRejectsOverlap() {
        val first = ShipPlacement(ShipType.DESTROYER, Cell(0, 0), vertical = false)
        val overlap = ShipPlacement(ShipType.SUBMARINE, Cell(0, 1), vertical = true)
        assertEquals(PlacementError.OVERLAP, GameEngine.canPlace(listOf(first), overlap, false).reason)
    }

    @Test fun noTouchingRejectsAdjacentShipsIncludingDiagonal() {
        val first = ShipPlacement(ShipType.DESTROYER, Cell(0, 0), vertical = false)
        val edge = ShipPlacement(ShipType.SUBMARINE, Cell(1, 2), vertical = false)
        assertEquals(PlacementError.TOUCHING, GameEngine.canPlace(listOf(first), edge, true).reason)
        assertTrue(GameEngine.canPlace(listOf(first), edge, false).valid)
    }

    @Test fun fleetValidationRequiresExactlyOneOfEveryShip() {
        val randomFleet = GameEngine.randomFleet(random = Random(7))
        assertTrue(GameEngine.validateFleet(randomFleet, false).valid)
        assertEquals(PlacementError.WRONG_FLEET, GameEngine.validateFleet(randomFleet.dropLast(1), false).reason)
        val duplicate = randomFleet.dropLast(1) + randomFleet.first()
        assertFalse(GameEngine.validateFleet(duplicate, false).valid)
    }

    @Test fun completeFleetPreservesShipsAlreadyPlaced() {
        val placed = listOf(ShipPlacement(ShipType.CARRIER, Cell(0, 0), vertical = true))
        val complete = GameEngine.completeFleet(placed, noTouching = true, random = Random(19))
        assertEquals(5, complete.size)
        assertTrue(complete.contains(placed.single()))
        assertTrue(GameEngine.validateFleet(complete, true).valid)
    }

    @Test fun randomFleetAlwaysFitsAndHonorsNoTouching() {
        repeat(80) { seed ->
            val fleet = GameEngine.randomFleet(noTouching = true, random = Random(seed))
            assertEquals(5, fleet.size)
            assertTrue(GameEngine.validateFleet(fleet, true).valid)
        }
    }

    @Test fun firingResolvesMissHitAndSunk() {
        val ship = ShipPlacement(ShipType.DESTROYER, Cell(0, 0), vertical = false)
        assertEquals(ShotKind.MISS, GameEngine.resolveShot(listOf(ship), emptySet(), Cell(9, 9)).kind)
        assertEquals(ShotKind.HIT, GameEngine.resolveShot(listOf(ship), emptySet(), Cell(0, 0)).kind)
        val sunk = GameEngine.resolveShot(listOf(ship), setOf(Cell(0, 0)), Cell(0, 1))
        assertEquals(ShotKind.SUNK, sunk.kind)
        assertNotNull(sunk.sunkShip)
        assertEquals(listOf(Cell(0, 0), Cell(0, 1)), sunk.sunkShip?.cells)
    }

    @Test(expected = IllegalArgumentException::class)
    fun firingSameCellTwiceIsRejectedByEngine() {
        val ship = ShipPlacement(ShipType.DESTROYER, Cell(0, 0), vertical = false)
        GameEngine.resolveShot(listOf(ship), setOf(Cell(0, 0)), Cell(0, 0))
    }

    @Test fun turnOrderSkipsEliminatedPlayers() {
        val order = listOf("a", "b", "c", "d")
        assertEquals("d", GameEngine.nextAlive(order, "b", setOf("a", "d")))
        assertEquals("a", GameEngine.nextAlive(order, "d", setOf("a", "c")))
        assertEquals("c", GameEngine.nextAlive(order, "b", setOf("c")))
    }

    @Test fun winnerExistsOnlyForSingleSurvivor() {
        assertNull(GameEngine.winner(setOf("a", "b")))
        assertEquals("c", GameEngine.winner(setOf("c")))
        assertNull(GameEngine.winner(emptySet()))
    }
}
