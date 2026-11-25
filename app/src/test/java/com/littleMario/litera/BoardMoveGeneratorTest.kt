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

    @Test
    fun canUseExistingTileInMiddleOfGeneratedWord() {
        val generator = BoardMoveGenerator(dictionary(listOf("kot")))
        val board = BoardState.empty().withTile(7, 7, BoardTile('O'))
        val rack = requireNotNull(LetterRack.parse("KT"))

        val moves = generator.generate(board, rack)

        assertTrue(moves.any { move ->
            move.word == "kot" &&
                move.horizontal &&
                move.row == 7 &&
                move.column == 6 &&
                move.placements.map { it.row to it.column }.toSet() ==
                    setOf(7 to 6, 7 to 8)
        })
    }

    @Test
    fun canExtendExistingWordVertically() {
        val generator = BoardMoveGenerator(dictionary(listOf("kot")))
        val board = BoardState.empty().withTile(7, 7, BoardTile('K'))
        val rack = requireNotNull(LetterRack.parse("OT"))

        val moves = generator.generate(board, rack)

        assertTrue(moves.any { move ->
            move.word == "kot" &&
                !move.horizontal &&
                move.row == 7 &&
                move.column == 7 &&
                move.placements.size == 2 &&
                move.placements.any { it.row == 8 && it.column == 7 } &&
                move.placements.any { it.row == 9 && it.column == 7 }
        })
    }

    @Test
    fun repeatedRackLettersAreUsedOnlyAsManyTimesAsAvailable() {
        val generator = BoardMoveGenerator(dictionary(listOf("ala")))
        val rack = requireNotNull(LetterRack.parse("AAL"))

        val moves = generator.generate(BoardState.empty(), rack)
            .filter { it.word == "ala" }

        assertTrue(moves.isNotEmpty())
        assertTrue(moves.all { move ->
            move.placements.size == 3 &&
                move.placements.count { it.tile.letter == 'A' && !it.tile.isBlank } == 2 &&
                move.placements.count { it.tile.letter == 'L' && !it.tile.isBlank } == 1
        })
    }

    @Test
    fun supportsTwoBlankTilesInOneMove() {
        val generator = BoardMoveGenerator(dictionary(listOf("ala")))
        val rack = requireNotNull(LetterRack.parse("A??"))

        val moves = generator.generate(BoardState.empty(), rack)
            .filter { it.word == "ala" }

        assertTrue(moves.any { move ->
            move.placements.size == 3 &&
                move.placements.count { it.tile.isBlank } == 2
        })
    }

    @Test
    fun doesNotReturnDuplicateMoves() {
        val generator = BoardMoveGenerator(dictionary(listOf("ala", "ala")))
        val rack = requireNotNull(LetterRack.parse("AAL"))

        val moves = generator.generate(BoardState.empty(), rack)
        val keys = moves.map { move ->
            val blankPattern = move.placements
                .filter { it.tile.isBlank }
                .joinToString(",") { "${it.row}:${it.column}" }
            "${move.horizontal}:${move.row}:${move.column}:${move.word}:$blankPattern"
        }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun rejectsMoveWhenAnyOfSeveralCrossWordsIsInvalid() {
        val generator = BoardMoveGenerator(dictionary(listOf("ko", "ake")))
        val board = BoardState.empty()
            .withTile(6, 6, BoardTile('A'))
            .withTile(8, 6, BoardTile('E'))
            .withTile(6, 7, BoardTile('A'))
            .withTile(8, 7, BoardTile('E'))
        val rack = requireNotNull(LetterRack.parse("KO"))

        val moves = generator.generate(board, rack)

        // Pierwsza nowa litera tworzy AKE, druga AOE. Brak AOE w słowniku
        // musi unieważnić cały ruch, nawet jeśli AKE jest poprawne.
        assertTrue(moves.none { move ->
            move.word == "ko" &&
                move.horizontal &&
                move.row == 7 &&
                move.column == 6
        })
    }
}
