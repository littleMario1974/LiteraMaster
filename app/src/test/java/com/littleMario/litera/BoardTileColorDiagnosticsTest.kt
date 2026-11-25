package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTileColorDiagnosticsTest {

    @Test
    fun countsOnlyCellsWithRecognizedLetters() {
        val results = listOf(
            BoardCellOcrResult(0, 0, 'A', "A", 0.98f, TileColor.YELLOW),
            BoardCellOcrResult(0, 1, 'B', "B", 0.95f, TileColor.YELLOW),
            BoardCellOcrResult(0, 2, 'C', "C", 0.90f, TileColor.GREEN),
            BoardCellOcrResult(0, 3, 'D', "D", 0.90f, null),
            BoardCellOcrResult(0, 4, null, "", null, TileColor.WHITE)
        )

        val diagnostics = BoardTileColorDiagnostics.from(results)

        assertEquals(2, diagnostics.colorCounts[TileColor.YELLOW])
        assertEquals(1, diagnostics.colorCounts[TileColor.GREEN])
        assertEquals(1, diagnostics.colorCounts[null])
        assertEquals(4, diagnostics.colorCounts.values.sum())
        assertTrue(diagnostics.suspectedBlankPositions.isEmpty())
    }

    @Test
    fun listsWhiteTilesAsCandidatesWithoutChangingTheirLetter() {
        val results = listOf(
            BoardCellOcrResult(2, 4, 'A', "A", 0.98f, TileColor.WHITE),
            BoardCellOcrResult(5, 7, 'E', "E", 0.93f, TileColor.WHITE),
            BoardCellOcrResult(1, 1, 'N', "N", 0.90f, TileColor.RED)
        )

        val diagnostics = BoardTileColorDiagnostics.from(results)

        assertEquals(2, diagnostics.colorCounts[TileColor.WHITE])
        assertEquals(setOf(2 to 4, 5 to 7), diagnostics.suspectedBlankPositions)
    }

    @Test
    fun returnsEmptySummaryWhenNoLettersWereRecognized() {
        val diagnostics = BoardTileColorDiagnostics.from(
            listOf(BoardCellOcrResult(0, 0, null, "?", 0.2f, TileColor.WHITE))
        )

        assertTrue(diagnostics.colorCounts.isEmpty())
        assertTrue(diagnostics.suspectedBlankPositions.isEmpty())
    }
}
