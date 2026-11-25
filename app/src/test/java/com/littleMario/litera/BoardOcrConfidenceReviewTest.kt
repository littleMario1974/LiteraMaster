package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardOcrConfidenceReviewTest {

    @Test
    fun flagsRecognizedLettersBelowConfidenceThreshold() {
        val results = listOf(
            BoardCellOcrResult(0, 0, 'A', "A", 0.64f),
            BoardCellOcrResult(0, 1, 'B', "B", 0.65f),
            BoardCellOcrResult(0, 2, 'C', "C", 0.90f),
            BoardCellOcrResult(0, 3, null, "?", 0.10f)
        )

        assertEquals(
            setOf(0 to 0),
            BoardOcrConfidenceReview.lowConfidencePositions(results)
        )
    }

    @Test
    fun doesNotFlagMissingConfidenceOrUnrecognizedText() {
        val results = listOf(
            BoardCellOcrResult(1, 1, 'A', "A", null),
            BoardCellOcrResult(1, 2, null, "?", 0.20f)
        )

        assertTrue(BoardOcrConfidenceReview.lowConfidencePositions(results).isEmpty())
    }
}
