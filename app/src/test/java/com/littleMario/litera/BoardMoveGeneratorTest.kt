package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardMoveGeneratorTest {

    private fun dictionary(words: List<String>): Array<Node> {
        val nodes = mutableListOf(Node())
        for (word in words) {
            var current = 0
            for (letter in word) {
                val index = DawgWordFinder.ALPHABET.indexOf(letter)
                require(index >= 0)
                var child = nodes[current].next[index]
                if (child == -1) {
                    child = nodes.size
                    nodes[current].next[index] = child
                    nodes.add(Node())
                }
                current = child
            }
            nodes[current].terminal = true
        }
        return nodes.toTypedArray()
    }

    @Test
    fun firstMoveMustCoverCenter() {
        val generator = BoardMoveGenerator(dictionary(listOf("miś")))
        val rack = requireNotNull(LetterRack.parse("MIŚ"))

        val moves = generator.generate(BoardState.empty(), rack)

        assertTrue(moves.isNotEmpty())
        assertTrue(moves.all { it.placements.size == 3 })
        assertTrue(moves.any { it.word == "miś" && it.horizontal && it.row == 7 && it.column == 6 })
        assertTrue(moves.all { move ->
            if (move.horizontal) {
                move.row == 7 && 7 in (move.column until move.column + move.word.length)
            } else {
                move.column == 7 && 7 in (move.row until move.row + move.word.length)
            }
        })
    }

    @Test
    fun moveCanExtendExistingLetter() {
        val generator = BoardMoveGenerator(dictionary(listOf("miś")))
        val board = BoardState.empty().withTile(7, 7, BoardTile('M'))
        val rack = requireNotNull(LetterRack.parse("IŚ"))

        val moves = generator.generate(board, rack)

        assertTrue(moves.any { move ->
            move.word == "miś" &&
                move.placements.size == 2 &&
                move.placements.any { it.row == 7 && it.column == 8 } &&
                move.placements.any { it.row == 7 && it.column == 9 }
        })
    }

    @Test
    fun rejectsWordsThatDoNotTouchCurrentBoard() {
        val generator = BoardMoveGenerator(dictionary(listOf("miś", "ala")))
        val board = BoardState.empty().withTile(0, 0, BoardTile('M'))
        val rack = requireNotNull(LetterRack.parse("ALA"))

        val moves = generator.generate(board, rack)

        assertTrue(moves.none { it.word == "ala" })
    }

    @Test
    fun usesBlankAsAnyLetter() {
        val generator = BoardMoveGenerator(dictionary(listOf("miś")))
        val rack = requireNotNull(LetterRack.parse("MI?"))

        val moves = generator.generate(BoardState.empty(), rack)

        assertTrue(moves.any { move ->
            move.word == "miś" && move.placements.any { it.tile.isBlank && it.tile.letter == 'Ś' }
        })
    }
    @Test
    fun keepsAlternativeBlankAssignments() {
        val generator = BoardMoveGenerator(dictionary(listOf("miś")))
        val rack = requireNotNull(LetterRack.parse("MIŚ?"))

        val moves = generator.generate(BoardState.empty(), rack)
            .filter { it.word == "miś" && it.horizontal && it.row == 7 && it.column == 6 }

        assertTrue(moves.any { it.placements.none { placed -> placed.tile.isBlank } })
        assertTrue(moves.any { it.placements.any { placed -> placed.tile.isBlank && placed.tile.letter == 'Ś' } })
    }


    @Test
    fun rejectsMoveWhenItCreatesInvalidCrossWord() {
        val generator = BoardMoveGenerator(dictionary(listOf("kot")))
        val board = BoardState.empty()
            .withTile(6, 7, BoardTile('A'))
            .withTile(8, 7, BoardTile('E'))
        val rack = requireNotNull(LetterRack.parse("KOT"))

        val moves = generator.generate(board, rack)

        // Położenie O między A i E utworzyłoby pionowe słowo AOE,
        // którego nie ma w słowniku.
        assertTrue(moves.none { move ->
            move.word == "kot" &&
                move.horizontal &&
                move.row == 7 &&
                move.column == 6
        })
    }

    @Test
    fun acceptsMoveWhenEveryCreatedCrossWordIsInDictionary() {
        val generator = BoardMoveGenerator(dictionary(listOf("kot", "aoe")))
        val board = BoardState.empty()
            .withTile(6, 7, BoardTile('A'))
            .withTile(8, 7, BoardTile('E'))
        val rack = requireNotNull(LetterRack.parse("KOT"))

        val moves = generator.generate(board, rack)

        assertTrue(moves.any { move ->
            move.word == "kot" &&
                move.horizontal &&
                move.row == 7 &&
                move.column == 6
        })
    }
}