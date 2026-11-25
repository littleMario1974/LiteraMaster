package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LetterRackTest {

    @Test
    fun parsesPolishLettersAndNormalizesCase() {
        val rack = LetterRack.parse("mIśĄ")

        assertEquals(listOf('M', 'I', 'Ś', 'Ą'), rack?.letters)
    }

    @Test
    fun acceptsBlankTile() {
        val rack = LetterRack.parse("ABC?")

        assertEquals(1, rack?.blankCount)
        assertEquals(4, rack?.size)
    }

    @Test
    fun ignoresWhitespace() {
        assertEquals(
            listOf('A', 'B', 'C'),
            LetterRack.parse("a b c")?.letters
        )
    }

    @Test
    fun rejectsMoreThanSevenTiles() {
        assertNull(LetterRack.parse("ABCDEFGH"))
    }

    @Test
    fun rejectsCharactersOutsidePolishAlphabet() {
        assertNull(LetterRack.parse("ABQ"))
        assertNull(LetterRack.parse("A1B"))
    }

    @Test
    fun countsRepeatedLetters() {
        val rack = LetterRack.parse("AAĄŚA")!!

        assertEquals(3, rack.count('a'))
        assertEquals(1, rack.count('ś'))
    }
}
