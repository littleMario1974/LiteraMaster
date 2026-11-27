package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardTileTextParserTest {

    @Test
    fun parsesPolishUppercaseLetter() {
        assertEquals('Ś', BoardTileTextParser.parse("Ś"))
    }

    @Test
    fun parsesLowercasePolishLetter() {
        assertEquals('Ł', BoardTileTextParser.parse("ł"))
    }

    @Test
    fun allowsTrailingTileScoreDigits() {
        assertEquals('Ż', BoardTileTextParser.parse("Ż 10"))
    }

    @Test
    fun rejectsPremiumSquareLabels() {
        assertNull(BoardTileTextParser.parse("2L"))
        assertNull(BoardTileTextParser.parse("3S"))
        assertNull(BoardTileTextParser.parse("2 x L"))
    }

    @Test
    fun returnsNullForEmptyText() {
        assertNull(BoardTileTextParser.parse(""))
    }

    @Test
    fun returnsNullWhenMoreThanOneDifferentLetterIsFound() {
        assertNull(BoardTileTextParser.parse("MI"))
    }

    @Test
    fun returnsNullWhenSameLetterIsReadMoreThanOnce() {
        assertNull(BoardTileTextParser.parse("AA"))
    }
}
