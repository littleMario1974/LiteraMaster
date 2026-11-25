package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardOcrConfidenceTest {

    @Test
    fun ignoresConfidenceOfTileScoreDigits() {
        val confidence = BoardOcrConfidence.calculate(
            listOf("A" to 0.90f, "10" to 0.20f)
        )

        assertEquals(0.90f, confidence!!, 0.0001f)
    }

    @Test
    fun averagesConfidenceOfElementsContainingLetters() {
        val confidence = BoardOcrConfidence.calculate(
            listOf("A" to 0.80f, "Ż" to 0.60f, "10" to 0.10f)
        )

        assertEquals(0.70f, confidence!!, 0.0001f)
    }

    @Test
    fun returnsNullWhenNoLetterElementHasConfidence() {
        val confidence = BoardOcrConfidence.calculate(
            listOf("10" to 0.95f, "?" to 0.50f, "A" to null)
        )

        assertNull(confidence)
    }
}
