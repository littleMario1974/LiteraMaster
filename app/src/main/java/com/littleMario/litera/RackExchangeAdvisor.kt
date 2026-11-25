package com.littleMario.litera

import kotlin.random.Random

/**
 * Statystyczny doradca wymiany stojaka.
 *
 * To model heurystyczny, a nie symulator całej partii: nie znamy stojaka
 * przeciwnika ani jego przyszłego ruchu. Nieznane płytki przeciwnika są
 * losowo rezerwowane przed symulacją dobrania z woreczka.
 */
data class RackExchangeAdvice(
    val exchangeAllowed: Boolean,
    val shouldExchange: Boolean,
    val exchangeLetters: List<Char>,
    val keepLetters: List<Char>,
    val currentRackQuality: Double,
    val expectedRackQualityAfterExchange: Double,
    val improvement: Double,
    val estimatedBagTiles: Int,
    val exchangesRemaining: Int,
    val explanation: String
)

object LiterakiTileDistribution {
    /** Pełny zestaw 100 płytek według zasad Literaków Kurnika. */
    val counts: Map<Char, Int> = linkedMapOf(
        'A' to 9, 'Ą' to 1, 'B' to 2, 'C' to 3, 'Ć' to 1,
        'D' to 3, 'E' to 7, 'Ę' to 1, 'F' to 1, 'G' to 2,
        'H' to 2, 'I' to 8, 'J' to 2, 'K' to 3, 'L' to 3,
        'Ł' to 2, 'M' to 3, 'N' to 5, 'Ń' to 1, 'O' to 6,
        'Ó' to 1, 'P' to 3, 'R' to 4, 'S' to 4, 'Ś' to 1,
        'T' to 3, 'U' to 2, 'W' to 4, 'Y' to 4, 'Z' to 5,
        'Ź' to 1, 'Ż' to 1, '?' to 2
    )

    const val TOTAL_TILES = 100
    const val MAX_EXCHANGES_PER_PLAYER = 3
    const val MIN_TILES_TO_EXCHANGE = 7

    fun asTileList(): List<Char> = counts.flatMap { (letter, count) ->
        List(count) { letter }
    }
}

class RackExchangeAdvisor(
    private val simulationsPerCandidate: Int = 160,
    private val randomSeed: Int = 1974,
    private val minimumImprovement: Double = 1.15
) {
    init {
        require(simulationsPerCandidate > 0)
        require(minimumImprovement >= 0.0)
    }

    /**
     * opponentRackSize=7 jest założeniem dla pełnego stojaka przeciwnika.
     * Ponieważ jego liter nie znamy, wynik i liczba płytek w worku są szacunkowe.
     */
    fun recommend(
        board: BoardState,
        rack: LetterRack,
        opponentRackSize: Int = LetterRack.MAX_TILES,
        exchangesUsed: Int = 0
    ): RackExchangeAdvice {
        require(opponentRackSize >= 0)
        require(exchangesUsed >= 0)

        val unseenTiles = remainingUnknownTiles(board, rack)
        val estimatedBagTiles = (unseenTiles.size - opponentRackSize).coerceAtLeast(0)
        val exchangesRemaining =
            (LiterakiTileDistribution.MAX_EXCHANGES_PER_PLAYER - exchangesUsed).coerceAtLeast(0)
        val currentQuality = rackQuality(rack.letters)

        val unavailableReason = when {
            exchangesRemaining == 0 -> "Wykorzystano już wszystkie trzy wymiany."
            estimatedBagTiles < LiterakiTileDistribution.MIN_TILES_TO_EXCHANGE ->
                "Szacowana liczba płytek w woreczku jest mniejsza niż 7."
            rack.size == 0 -> "Stojak jest pusty."
            else -> null
        }

        if (unavailableReason != null) {
            return RackExchangeAdvice(
                exchangeAllowed = false,
                shouldExchange = false,
                exchangeLetters = emptyList(),
                keepLetters = rack.letters,
                currentRackQuality = currentQuality,
                expectedRackQualityAfterExchange = currentQuality,
                improvement = 0.0,
                estimatedBagTiles = estimatedBagTiles,
                exchangesRemaining = exchangesRemaining,
                explanation = unavailableReason
            )
        }

        val unseen = unseenTiles.toCharArray()
        var bestMask = 0
        var bestExpectedQuality = currentQuality

        // Maksymalnie 7 płytek, więc sprawdzamy wszystkie 2^n - 1 podzbiory.
        val candidateCount = 1 shl rack.size
        for (mask in 1 until candidateCount) {
            val exchangeCount = Integer.bitCount(mask)
            if (exchangeCount > estimatedBagTiles) continue

            val retained = rack.letters.filterIndexed { index, _ ->
                mask and (1 shl index) == 0
            }
            var qualityTotal = 0.0

            repeat(simulationsPerCandidate) { simulation ->
                // Wystarczy wylosować pierwsze opponent + exchangeCount pozycji.
                // Częściowe Fisher-Yates daje taki sam rozkład próby jak pełne tasowanie,
                // ale nie miesza pozostałych płytek, których ta symulacja nie wykorzysta.
                val sample = unseen.copyOf()
                val random = Random(randomSeed + mask * 1009 + simulation)
                val sampleSize = opponentRackSize + exchangeCount
                for (index in 0 until sampleSize) {
                    val selectedIndex = random.nextInt(index, sample.size)
                    val temporary = sample[index]
                    sample[index] = sample[selectedIndex]
                    sample[selectedIndex] = temporary
                }

                // Pierwsze pozycje rezerwujemy dla nieznanego stojaka przeciwnika.
                val candidateRack = ArrayList<Char>(rack.size)
                candidateRack.addAll(retained)
                for (index in opponentRackSize until sampleSize) {
                    candidateRack.add(sample[index])
                }
                qualityTotal += rackQuality(candidateRack)
            }

            val expectedQuality = qualityTotal / simulationsPerCandidate
            if (expectedQuality > bestExpectedQuality) {
                bestExpectedQuality = expectedQuality
                bestMask = mask
            }
        }

        val selectedForExchange = rack.letters.filterIndexed { index, _ ->
            bestMask and (1 shl index) != 0
        }
        val kept = rack.letters.filterIndexed { index, _ ->
            bestMask and (1 shl index) == 0
        }
        val improvement = bestExpectedQuality - currentQuality
        val shouldExchange = bestMask != 0 && improvement >= minimumImprovement

        return RackExchangeAdvice(
            exchangeAllowed = true,
            shouldExchange = shouldExchange,
            exchangeLetters = if (shouldExchange) selectedForExchange else emptyList(),
            keepLetters = if (shouldExchange) kept else rack.letters,
            currentRackQuality = currentQuality,
            expectedRackQualityAfterExchange = bestExpectedQuality,
            improvement = improvement,
            estimatedBagTiles = estimatedBagTiles,
            exchangesRemaining = exchangesRemaining,
            explanation = if (shouldExchange) {
                "Symulacja wskazuje poprawę jakości stojaka po wymianie."
            } else {
                "Przewidywana poprawa jest zbyt mała, aby uzasadnić utratę kolejki."
            }
        )
    }

    private fun remainingUnknownTiles(
        board: BoardState,
        rack: LetterRack
    ): List<Char> {
        val remaining = LiterakiTileDistribution.counts.toMutableMap()

        fun removeTile(letter: Char) {
            val key = letter.uppercaseChar()
            val count = remaining[key] ?: return
            if (count > 0) remaining[key] = count - 1
        }

        board.occupiedTiles().forEach { placed ->
            removeTile(if (placed.tile.isBlank) '?' else placed.tile.normalizedLetter)
        }
        rack.letters.forEach(::removeTile)

        return remaining.flatMap { (letter, count) -> List(count.coerceAtLeast(0)) { letter } }
    }

    /**
     * Jakość stojaka w umownej skali. Preferuje blanki, równowagę samogłosek
     * i spółgłosek oraz różnorodność; łagodnie karze nadmiar duplikatów
     * i kilka trudnych, rzadkich liter naraz.
     */
    internal fun rackQuality(letters: List<Char>): Double {
        if (letters.isEmpty()) return 0.0

        val normalized = letters.map { it.uppercaseChar() }
        val vowels = setOf('A', 'Ą', 'E', 'Ę', 'I', 'O', 'Ó', 'U', 'Y')
        val vowelCount = normalized.count { it in vowels }
        val targetVowels = letters.size * 0.39
        var quality = 5.0 - kotlin.math.abs(vowelCount - targetVowels) * 1.55

        quality += normalized.count { it == '?' } * 4.2

        val counts = normalized.filter { it != '?' }.groupingBy { it }.eachCount()
        counts.forEach { (letter, count) ->
            if (count > 1) {
                quality -= (count - 1) * 0.75
                quality -= (count - 2).coerceAtLeast(0) * 1.0
            }
            if ((LiterakiTileDistribution.counts[letter] ?: 0) == 1) {
                quality -= 0.45
            }
        }

        val difficultLetters = setOf('Ą', 'Ć', 'Ę', 'Ń', 'Ó', 'Ź', 'Ż', 'F')
        val difficultCount = normalized.count { it in difficultLetters }
        quality -= (difficultCount - 1).coerceAtLeast(0) * 0.8

        val consonantCount = normalized.count { it != '?' && it !in vowels }
        if (vowelCount == 0 && consonantCount >= 4) quality -= 1.5
        if (vowelCount == letters.size && letters.size >= 4) quality -= 1.5

        return quality
    }
}
