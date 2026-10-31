package com.friends.ships.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BotTest {
    @Test fun botNeverRepeatsAndStaysOnBoard() {
        val bot = HuntTargetBot(Random(3))
        val shots = mutableSetOf<Cell>()
        repeat(100) {
            val next = bot.nextShot()
            assertTrue(next.isOnBoard())
            assertTrue(shots.add(next))
            bot.recordShot(next, if (it == 0) ShotKind.HIT else ShotKind.MISS)
        }
        assertEquals(100, shots.size)
    }

    @Test fun botTargetsNeighborsAfterHit() {
        val bot = HuntTargetBot(Random(1))
        val hit = Cell(5, 5)
        bot.recordShot(hit, ShotKind.HIT)
        val next = bot.nextShot()
        val distance = kotlin.math.abs(next.row - hit.row) + kotlin.math.abs(next.col - hit.col)
        assertEquals(1, distance)
    }
}
