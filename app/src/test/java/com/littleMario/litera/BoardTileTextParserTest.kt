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
    fun ignoresTileScoreDigits() {
        assertEquals('Ż', BoardTileTextParser.parse("Ż 10"))
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
