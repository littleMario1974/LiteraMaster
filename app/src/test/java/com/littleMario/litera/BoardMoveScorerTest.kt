package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardMoveScorerTest {

    @Test
    fun scoresMainWordAndWordMultiplier() {
        val board = BoardState.empty()
        val move = BoardMove(
            word = "kot",
            row = 7,
            column = 6,
            horizontal = true,
            placements = listOf(
                PlacedTile(7, 6, BoardTile('K')),
                PlacedTile(7, 7, BoardTile('O')),
                PlacedTile(7, 8, BoardTile('T'))
            )
        )

        val scorer = BoardMoveScorer { row, column ->
            if (row == 7 && column == 7) BoardPremium(wordMultiplier = 2)
            else BoardPremium.NONE
        }

        // K=2, O=1, T=2; środkowa premia słowna x2.
        assertEquals(10, scorer.score(board, move))
    }

    @Test
    fun blankHasZeroPointsEvenOnLetterPremium() {
        val board = BoardState.empty()
        val move = BoardMove(
            word = "kot",
            row = 7,
            column = 6,
            horizontal = true,
            placements = listOf(
                PlacedTile(7, 6, BoardTile('K')),
                PlacedTile(7, 7, BoardTile('O', isBlank = true)),
                PlacedTile(7, 8, BoardTile('T'))
            )
        )

        val scorer = BoardMoveScorer { row, column ->
            if (row == 7 && column == 7) BoardPremium(letterMultiplier = 3)
            else BoardPremium.NONE
        }

        assertEquals(4, scorer.score(board, move))
    }

    @Test
    fun addsFiftyPointBonusForSevenNewTiles() {
        val board = BoardState.empty()
        val word = "abcdefg"
        val placements = word.mapIndexed { index, letter ->
            PlacedTile(7, index + 4, BoardTile(letter.uppercaseChar()))
        }
        val move = BoardMove(word, 7, 4, true, placements)

        // A=1, B=3, C=2, D=2, E=1, F=5, G=3 => 17 + 50.
        assertEquals(67, BoardMoveScorer().score(board, move))
    }

    @Test
    fun scoresCrossWordSeparately() {
        val board = BoardState.empty()
            .withTile(6, 7, BoardTile('A'))
            .withTile(8, 7, BoardTile('E'))
        val move = BoardMove(
            word = "kot",
            row = 7,
            column = 6,
            horizontal = true,
            placements = listOf(
                PlacedTile(7, 6, BoardTile('K')),
                PlacedTile(7, 7, BoardTile('O')),
                PlacedTile(7, 8, BoardTile('T'))
            )
        )

        // Główne KOT = 5; pionowe AOE = 3.
        assertEquals(8, BoardMoveScorer().score(board, move))
    }
}
