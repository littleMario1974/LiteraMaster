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
    fun recommendationIsDeterministicForTheSameSeedAndInput() {
        val advisor = RackExchangeAdvisor(
            simulationsPerCandidate = 32,
            randomSeed = 8128
        )
        val rack = LetterRack.parse("ŹŻĄĘĆŃF")!!

        val first = advisor.recommend(BoardState.empty(), rack)
        val second = advisor.recommend(BoardState.empty(), rack)

        assertEquals(first, second)
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

    @Test
    fun optimizedRackQualityMatchesReferenceScoringForPolishRacksAndBlanks() {
        val racks = listOf(
            "AEINORT",
            "ĄĘÓŹŻF?",
            "AAAAEEE",
            "??AĄBC",
            "ŚŁŃRZŻY",
            "AEI",
            "BBBBBBB"
        ).map { LetterRack.parse(it)!!.letters } + listOf(
            listOf('a', 'ą', '?', 'f'),
            emptyList()
        )

        racks.forEach { rack ->
            assertEquals(
                "Different rack quality for rack: $rack",
                referenceRackQuality(rack),
                RackExchangeAdvisor().rackQuality(rack),
                0.000001
            )
        }
    }

    /**
     * Niezależna implementacja wcześniejszego algorytmu kolekcyjnego.
     * Służy jako punkt odniesienia dla zoptymalizowanej wersji tablicowej.
     */
    private fun referenceRackQuality(letters: List<Char>): Double {
        if (letters.isEmpty()) return 0.0

        val normalized = letters.map { it.uppercaseChar() }
        val vowels = setOf('A', 'Ą', 'E', 'Ę', 'I', 'O', 'Ó', 'U', 'Y')
        val difficultLetters = setOf('Ą', 'Ć', 'Ę', 'Ń', 'Ó', 'Ź', 'Ż', 'F')
        val vowelCount = normalized.count { it in vowels }
        val blankCount = normalized.count { it == '?' }
        val consonantCount = normalized.count { it !in vowels && it != '?' }
        val difficultCount = normalized.count { it in difficultLetters }
        val letterCounts = normalized
            .filter { it != '?' }
            .groupingBy { it }
            .eachCount()

        val targetVowels = normalized.size * 0.39
        var quality = 5.0 - kotlin.math.abs(vowelCount - targetVowels) * 1.55
        quality += blankCount * 4.2

        letterCounts.forEach { (letter, count) ->
            if (count > 1) {
                quality -= (count - 1) * 0.75
                quality -= (count - 2).coerceAtLeast(0) * 1.0
            }
            if (LiterakiTileDistribution.counts[letter] == 1) {
                quality -= 0.45
            }
        }

        quality -= (difficultCount - 1).coerceAtLeast(0) * 0.8
        if (vowelCount == 0 && consonantCount >= 4) quality -= 1.5
        if (vowelCount == normalized.size && normalized.size >= 4) quality -= 1.5
        return quality
    }
}
