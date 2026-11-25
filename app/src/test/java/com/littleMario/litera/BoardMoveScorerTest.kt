package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardMoveScorerTest {

    @Test
    fun appliesTripleWordPremiumOnlyWhenNewTileIsPlacedThere() {
        val board = BoardState.empty()
        val move = BoardMove(
            word = "kot",
            row = 2,
            column = 0,
            horizontal = true,
            placements = listOf(
                PlacedTile(2, 0, BoardTile('K')),
                PlacedTile(2, 1, BoardTile('O')),
                PlacedTile(2, 2, BoardTile('T'))
            )
        )

        // Pole (3,1) ma premię 3X: (K=2 + O=1 + T=2) * 3.
        assertEquals(15, BoardMoveScorer().score(board, move))
    }

    @Test
    fun matchingTileAndFieldColorsTripleLetterValue() {
        val board = BoardState.empty()
        val move = BoardMove(
            word = "fa",
            row = 0,
            column = 0,
            horizontal = true,
            placements = listOf(
                PlacedTile(0, 0, BoardTile('F')), // czerwona płytka na czerwonym polu
                PlacedTile(0, 1, BoardTile('A'))
            )
        )

        // F=5, zgodność kolorów daje 5*3; A=1. Pole (1,1) nie ma 3X.
        assertEquals(16, BoardMoveScorer().score(board, move))
    }

    @Test
    fun mismatchedTileAndFieldColorsDoNotGiveLetterPremium() {
        val board = BoardState.empty()
        val move = BoardMove(
            word = "ak",
            row = 0,
            column = 0,
            horizontal = true,
            placements = listOf(
                PlacedTile(0, 0, BoardTile('A')), // żółta płytka na czerwonym polu
                PlacedTile(0, 1, BoardTile('K'))
            )
        )

        assertEquals(3, BoardMoveScorer().score(board, move))
    }

    @Test
    fun blankHasZeroPointsEvenOnMatchingColorPremium() {
        val board = BoardState.empty()
        val move = BoardMove(
            word = "fa",
            row = 0,
            column = 0,
            horizontal = true,
            placements = listOf(
                PlacedTile(0, 0, BoardTile('F', isBlank = true)),
                PlacedTile(0, 1, BoardTile('A'))
            )
        )

        assertEquals(1, BoardMoveScorer().score(board, move))
    }

    @Test
    fun previouslyOccupiedPremiumFieldIsNotActivatedAgain() {
        val board = BoardState.empty().withTile(7, 7, BoardTile('F'))
        val move = BoardMove(
            word = "af",
            row = 7,
            column = 6,
            horizontal = true,
            placements = listOf(PlacedTile(7, 6, BoardTile('A')))
        )

        // F leży już na czerwonym polu centralnym: liczymy je tylko jako 5 pkt.
        assertEquals(6, BoardMoveScorer().score(board, move))
    }

    @Test
    fun previouslyOccupiedWordPremiumDoesNotMultiplyNewWordAgain() {
        val board = BoardState.empty().withTile(2, 0, BoardTile('K'))
        val move = BoardMove(
            word = "kot",
            row = 2,
            column = 0,
            horizontal = true,
            placements = listOf(
                PlacedTile(2, 1, BoardTile('O')),
                PlacedTile(2, 2, BoardTile('T'))
            )
        )

        // K leży już na polu 3X (3,1), więc premia słowna nie aktywuje się ponownie.
        assertEquals(5, BoardMoveScorer().score(board, move))
    }

    @Test
    fun addsFiftyPointBonusForSevenNewTiles() {
        val board = BoardState.empty()
        val word = "abcdefg"
        val placements = word.mapIndexed { index, letter ->
            PlacedTile(7, index + 4, BoardTile(letter.uppercaseChar()))
        }
        val move = BoardMove(word, 7, 4, true, placements)

        val scorerWithoutPremiums = BoardMoveScorer { _, _ -> BoardPremium.NONE }
        // A=1, B=3, C=2, D=2, E=1, F=5, G=3 => 17 + 50.
        assertEquals(67, scorerWithoutPremiums.score(board, move))
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

        // Główne KOT = 5; pionowe AOE = 3. O nie pasuje kolorem do czerwonego środka.
        assertEquals(8, BoardMoveScorer().score(board, move))
    }

    @Test
    fun premiumLayoutMatchesProvidedFieldCounts() {
        assertEquals(
            mapOf(
                "3X" to 8,
                "2X" to 16,
                "czerwone" to 17,
                "zielone" to 24,
                "żółte" to 20,
                "niebieskie" to 8
            ),
            LiterakiBoardLayout.premiumCounts()
        )
    }

    @Test
    fun scoresMultipleVerticalCrossWordsCreatedByOneHorizontalMove() {
        val board = BoardState.empty()
            .withTile(6, 6, BoardTile('A'))
            .withTile(8, 6, BoardTile('E'))
            .withTile(6, 7, BoardTile('A'))
            .withTile(8, 7, BoardTile('E'))
        val move = BoardMove(
            word = "ko",
            row = 7,
            column = 6,
            horizontal = true,
            placements = listOf(
                PlacedTile(7, 6, BoardTile('K')),
                PlacedTile(7, 7, BoardTile('O'))
            )
        )

        val result = BoardMoveScorer { _, _ -> BoardPremium.NONE }.explain(board, move)

        // Główne KO = 3; pionowe AKE = 4 oraz AOE = 3.
        assertEquals(3, result.mainWordPoints)
        assertEquals(listOf(4, 3), result.crossWordPoints)
        assertEquals(10, result.total)
    }

    @Test
    fun scoresMultipleHorizontalCrossWordsCreatedByOneVerticalMove() {
        val board = BoardState.empty()
            .withTile(6, 6, BoardTile('A'))
            .withTile(6, 8, BoardTile('E'))
            .withTile(7, 6, BoardTile('A'))
            .withTile(7, 8, BoardTile('E'))
        val move = BoardMove(
            word = "ko",
            row = 6,
            column = 7,
            horizontal = false,
            placements = listOf(
                PlacedTile(6, 7, BoardTile('K')),
                PlacedTile(7, 7, BoardTile('O'))
            )
        )

        val result = BoardMoveScorer { _, _ -> BoardPremium.NONE }.explain(board, move)

        // Główne KO = 3; poziome AKE = 4 oraz AOE = 3.
        assertEquals(3, result.mainWordPoints)
        assertEquals(listOf(4, 3), result.crossWordPoints)
        assertEquals(10, result.total)
    }

}
