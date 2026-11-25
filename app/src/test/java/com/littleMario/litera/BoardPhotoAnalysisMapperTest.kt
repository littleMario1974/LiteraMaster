package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardPhotoAnalysisMapperTest {

    private val geometry = BoardGridGeometry(
        topLeft = ImagePoint(0.0, 0.0),
        topRight = ImagePoint(150.0, 0.0),
        bottomRight = ImagePoint(150.0, 150.0),
        bottomLeft = ImagePoint(0.0, 150.0)
    )

    @Test
    fun mapsRecognizedLettersToCorrectBoardPositions() {
        val results = emptyResults().toMutableList()
        results[1 * BoardState.SIZE + 2] =
            BoardCellOcrResult(1, 2, 'M', "M", 0.98f)
        results[2 * BoardState.SIZE + 2] =
            BoardCellOcrResult(2, 2, 'I', "I", 0.96f)
        results[3 * BoardState.SIZE + 2] =
            BoardCellOcrResult(3, 2, 'Ś', "Ś", 0.91f)

        val analysis = BoardPhotoAnalysisMapper.map(geometry, results)

        assertEquals(3, analysis.recognizedTileCount)
        assertEquals('M', analysis.recognizedBoard.tileAt(1, 2)?.letter)
        assertEquals('I', analysis.recognizedBoard.tileAt(2, 2)?.letter)
        assertEquals('Ś', analysis.recognizedBoard.tileAt(3, 2)?.letter)
        assertNull(analysis.recognizedBoard.tileAt(0, 0))
        assertEquals(222, analysis.unresolvedCount)
    }

    @Test
    fun unresolvedResultsAreKeptForManualReview() {
        val results = emptyResults().toMutableList()
        results[0] = BoardCellOcrResult(0, 0, null, "M?", 0.42f)

        val analysis = BoardPhotoAnalysisMapper.map(geometry, results)

        assertEquals(1, analysis.unresolvedCells.count { it.rawText == "M?" })
        assertEquals(0, analysis.recognizedTileCount)
    }

    @Test
    fun whiteRecognizedTileRemainsOrdinaryUntilBlankIsConfirmedManually() {
        val results = emptyResults().toMutableList()
        results[0] = BoardCellOcrResult(0, 0, 'A', "A", 0.98f, TileColor.WHITE)

        val analysis = BoardPhotoAnalysisMapper.map(geometry, results)
        val tile = analysis.recognizedBoard.tileAt(0, 0)

        assertEquals('A', tile?.letter)
        assertEquals(false, tile?.isBlank)
    }

    @Test
    fun doesNotMarkColoredRecognizedTileAsBlank() {
        val results = emptyResults().toMutableList()
        results[0] = BoardCellOcrResult(0, 0, 'A', "A", 0.98f, TileColor.YELLOW)

        val analysis = BoardPhotoAnalysisMapper.map(geometry, results)

        assertEquals(false, analysis.recognizedBoard.tileAt(0, 0)?.isBlank)
    }

    private fun emptyResults(): List<BoardCellOcrResult> =
        (0 until BoardState.SIZE).flatMap { row ->
            (0 until BoardState.SIZE).map { column ->
                BoardCellOcrResult(row, column, null, "", null)
            }
        }
}
