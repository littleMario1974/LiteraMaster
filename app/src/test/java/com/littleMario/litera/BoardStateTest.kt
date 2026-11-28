package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardStateTest {

    @Test
    fun emptyBoardHas225EmptyFields() {
        val board = BoardState.empty()

        assertEquals(225, board.cells.flatten().size)
        assertEquals(0, board.occupiedTiles().size)
        assertFalse(board.hasTiles())
    }

    @Test
    fun boardWithAnyOccupiedFieldHasTiles() {
        val board = BoardState.empty()
            .withTile(14, 14, BoardTile('Ż'))

        assertTrue(board.hasTiles())
    }

    @Test
    fun canPlacePolishWordMisVertically() {
        var board = BoardState.empty()
        board = board.withTile(5, 7, BoardTile('M'))
        board = board.withTile(6, 7, BoardTile('I'))
        board = board.withTile(7, 7, BoardTile('Ś'))

        assertEquals('M', board.tileAt(5, 7)?.normalizedLetter)
        assertEquals('I', board.tileAt(6, 7)?.normalizedLetter)
        assertEquals('Ś', board.tileAt(7, 7)?.normalizedLetter)
        assertEquals(3, board.occupiedTiles().size)
        assertTrue(board.hasTiles())
    }

    @Test
    fun removingTileDoesNotChangeOtherFields() {
        val board = BoardState.empty()
            .withTile(5, 7, BoardTile('M'))
            .withTile(6, 7, BoardTile('I'))
            .withTile(7, 7, BoardTile('Ś'))

        val updated = board.withTile(6, 7, null)

        assertNull(updated.tileAt(6, 7))
        assertEquals('M', updated.tileAt(5, 7)?.normalizedLetter)
        assertEquals('Ś', updated.tileAt(7, 7)?.normalizedLetter)
        assertEquals(2, updated.occupiedTiles().size)
        assertTrue(updated.hasTiles())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncorrectBoardSize() {
        BoardState(List(14) { List<BoardTile?>(15) { null } })
    }
}
