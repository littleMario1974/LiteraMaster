package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DawgWordFinderTest {

    private fun dictionary(): Array<Node> {
        // Korzeń ma przejścia do słów: MIŚ oraz MI.
        val nodes = Array(4) { Node() }
        val m = DawgWordFinder.ALPHABET.indexOf('m')
        val i = DawgWordFinder.ALPHABET.indexOf('i')
        val s = DawgWordFinder.ALPHABET.indexOf('ś')
        nodes[0].next[m] = 1
        nodes[1].next[i] = 2
        nodes[2].terminal = true
        nodes[2].next[s] = 3
        nodes[3].terminal = true
        return nodes
    }

    @Test
    fun findsWordsFromRack() {
        val finder = DawgWordFinder(dictionary())
        val rack = requireNotNull(LetterRack.parse("MIŚ"))

        assertEquals(listOf("miś", "mi"), finder.findWords(rack))
    }

    @Test
    fun blankCanRepresentMissingLetter() {
        val finder = DawgWordFinder(dictionary())
        val rack = requireNotNull(LetterRack.parse("MI?"))

        assertEquals(listOf("miś", "mi"), finder.findWords(rack))
    }

    @Test
    fun doesNotReuseRackTiles() {
        val finder = DawgWordFinder(dictionary())
        val rack = requireNotNull(LetterRack.parse("M"))

        assertTrue(finder.findWords(rack).isEmpty())
    }
}
