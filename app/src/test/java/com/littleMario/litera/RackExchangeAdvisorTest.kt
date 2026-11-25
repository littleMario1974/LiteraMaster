package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RackExchangeAdvisorTest {

    @Test
    fun tileDistributionContainsExactlyOneHundredTilesAndTwoBlanks() {
        assertEquals(100, LiterakiTileDistribution.counts.values.sum())
        assertEquals(100, LiterakiTileDistribution.asTileList().size)
        assertEquals(2, LiterakiTileDistribution.counts['?'])
    }

    @Test
    fun estimatesBagAfterReservingBoardRackAndOpponentTiles() {
        val rack = LetterRack.parse("AEINORT")!!
        val advice = RackExchangeAdvisor(simulationsPerCandidate = 8).recommend(
            board = BoardState.empty(),
            rack = rack
        )

        assertEquals(86, advice.estimatedBagTiles)
        assertTrue(advice.exchangeAllowed)
    }

    @Test
    fun doesNotRecommendExchangeAfterThreeExchanges() {
        val advice = RackExchangeAdvisor(simulationsPerCandidate = 8).recommend(
            board = BoardState.empty(),
            rack = LetterRack.parse("ŹŻĄĘĆŃF")!!,
            exchangesUsed = 3
        )

        assertFalse(advice.exchangeAllowed)
        assertFalse(advice.shouldExchange)
        assertTrue(advice.exchangeLetters.isEmpty())
        assertEquals(0, advice.exchangesRemaining)
    }

    @Test
    fun blankIsNotSelectedForExchangeWhenRackHasOne() {
        val advice = RackExchangeAdvisor(simulationsPerCandidate = 24).recommend(
            board = BoardState.empty(),
            rack = LetterRack.parse("A?EINOR")!!
        )

        assertFalse(advice.exchangeLetters.contains('?'))
    }

    @Test
    fun recommendationKeepsOrExchangesOnlyLettersFromOriginalRack() {
        val rack = LetterRack.parse("ŹŻĄĘĆŃF")!!
        val advice = RackExchangeAdvisor(simulationsPerCandidate = 24).recommend(
            board = BoardState.empty(),
            rack = rack
        )

        assertTrue(rack.letters.containsAll(advice.exchangeLetters))
        assertTrue(rack.letters.containsAll(advice.keepLetters))
        assertEquals(rack.size, advice.exchangeLetters.size + advice.keepLetters.size)
    }
}
