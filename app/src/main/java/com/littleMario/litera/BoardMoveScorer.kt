package com.littleMario.litera

/**
 * Kolor płytki w Literakach. Kolor jednoznacznie wynika z litery.
 */
enum class TileColor(val points: Int) {
    YELLOW(1),
    GREEN(2),
    BLUE(3),
    RED(5),
    WHITE(0);

    companion object {
        fun forLetter(letter: Char, isBlank: Boolean = false): TileColor {
            if (isBlank) return WHITE
            return when (letter.uppercaseChar()) {
                'A', 'E', 'I', 'N', 'O', 'R', 'S', 'W', 'Z' -> YELLOW
                'C', 'D', 'K', 'L', 'M', 'P', 'T', 'Y' -> GREEN
                'B', 'G', 'H', 'J', 'Ł', 'U' -> BLUE
                'Ą', 'Ć', 'Ę', 'F', 'Ń', 'Ó', 'Ś', 'Ź', 'Ż' -> RED
                else -> error("Nieznana litera w zestawie Literaków: $letter")
            }
        }
    }
}

/**
 * Premia pola planszy. Premia literowa x3 działa wyłącznie wtedy,
 * gdy kolor nowej płytki jest zgodny z kolorem pola.
 */
data class BoardPremium(
    val fieldColor: TileColor? = null,
    val wordMultiplier: Int = 1
) {
    init {
        require(fieldColor != TileColor.WHITE) {
            "Białe pola nie są polami premiowymi."
        }
        require(wordMultiplier >= 1)
    }

    companion object {
        val NONE = BoardPremium()
    }
}

/**
 * Standardowy układ pól premiowych Literaków.
 * Współrzędne wejściowe z zasad (1..15) zostały przeliczone
 * na indeksy aplikacji (0..14), w kolejności (wiersz, kolumna).
 */
object LiterakiBoardLayout {
    private fun positions(vararg pairs: Pair<Int, Int>): Set<Pair<Int, Int>> =
        pairs.map { (row, column) -> (row - 1) to (column - 1) }.toSet()

    private val tripleWord = positions(
        3 to 1, 13 to 1,
        1 to 3, 15 to 3,
        1 to 13, 15 to 13,
        3 to 15, 13 to 15
    )

    private val doubleWord = positions(
        6 to 3, 10 to 3,
        5 to 4, 11 to 4,
        4 to 5, 12 to 5,
        3 to 6, 13 to 6,
        3 to 10, 13 to 10,
        4 to 11, 12 to 11,
        5 to 12, 11 to 12,
        6 to 13, 10 to 13
    )

    private val red = positions(
        1 to 1, 8 to 1, 15 to 1,
        7 to 2, 9 to 2,
        2 to 7, 14 to 7,
        1 to 8, 8 to 8, 15 to 8,
        2 to 9, 14 to 9,
        7 to 14, 9 to 14,
        1 to 15, 8 to 15, 15 to 15
    )

    private val green = positions(
        6 to 1, 10 to 1,
        5 to 2, 11 to 2,
        4 to 3, 12 to 3,
        3 to 4, 13 to 4,
        2 to 5, 14 to 5,
        1 to 6, 15 to 6,
        1 to 10, 15 to 10,
        2 to 11, 14 to 11,
        3 to 12, 13 to 12,
        4 to 13, 12 to 13,
        5 to 14, 11 to 14,
        6 to 15, 10 to 15
    )

    private val yellow = positions(
        8 to 3,
        7 to 4, 9 to 4,
        6 to 5, 10 to 5,
        5 to 6, 11 to 6,
        4 to 7, 12 to 7,
        3 to 8, 13 to 8,
        4 to 9, 12 to 9,
        5 to 10, 11 to 10,
        6 to 11, 10 to 11,
        7 to 12, 9 to 12,
        8 to 13
    )

    private val blue = positions(
        8 to 6,
        7 to 7, 9 to 7,
        6 to 8, 10 to 8,
        7 to 9, 9 to 9,
        8 to 10
    )

    fun premiumAt(row: Int, column: Int): BoardPremium {
        require(row in 0 until BoardState.SIZE && column in 0 until BoardState.SIZE)
        val position = row to column
        val wordMultiplier = when {
            position in tripleWord -> 3
            position in doubleWord -> 2
            else -> 1
        }
        val fieldColor = when {
            position in red -> TileColor.RED
            position in green -> TileColor.GREEN
            position in yellow -> TileColor.YELLOW
            position in blue -> TileColor.BLUE
            else -> null
        }
        return BoardPremium(fieldColor, wordMultiplier)
    }

    fun premiumCounts(): Map<String, Int> = mapOf(
        "3X" to tripleWord.size,
        "2X" to doubleWord.size,
        "czerwone" to red.size,
        "zielone" to green.size,
        "żółte" to yellow.size,
        "niebieskie" to blue.size
    )
}

/**
 * Liczy punkty za ruch. Premie dotyczą wyłącznie nowo dokładanych płytek.
 * Zajęte wcześniej pola nie aktywują premii ponownie.
 */
class BoardMoveScorer(
    private val premiumAt: (row: Int, column: Int) -> BoardPremium =
        LiterakiBoardLayout::premiumAt
) {
    fun score(board: BoardState, move: BoardMove): Int {
        require(move.placements.isNotEmpty()) { "Ruch nie zawiera nowych płytek." }

        val newTiles = move.placements.associateBy { it.row to it.column }
        val mainCoordinates = move.word.indices.map { index ->
            val row = move.row + if (move.horizontal) 0 else index
            val column = move.column + if (move.horizontal) index else 0
            row to column
        }

        var total = scoreWord(board, mainCoordinates, newTiles)

        // Każde słowo poprzeczne utworzone ruchem jest punktowane osobno.
        for (placed in move.placements) {
            val coordinates = collectCrossWord(board, placed, newTiles, move.horizontal)
            if (coordinates.size > 1) {
                total += scoreWord(board, coordinates, newTiles)
            }
        }

        if (move.placements.size == LetterRack.MAX_TILES) {
            total += SEVEN_TILE_BONUS
        }
        return total
    }

    private fun scoreWord(
        board: BoardState,
        coordinates: List<Pair<Int, Int>>,
        newTiles: Map<Pair<Int, Int>, PlacedTile>
    ): Int {
        var lettersTotal = 0
        var wordMultiplier = 1

        for ((row, column) in coordinates) {
            val key = row to column
            val newlyPlaced = newTiles[key]
            val tile = newlyPlaced?.tile ?: board.tileAt(row, column)
                ?: error("Brak płytki na pozycji ($row, $column).")
            val points = tileColorPoints(tile)
            if (newlyPlaced != null) {
                val premium = premiumAt(row, column)
                val matchingColor = !tile.isBlank &&
                    premium.fieldColor != null &&
                    TileColor.forLetter(tile.letter) == premium.fieldColor
                lettersTotal += points * if (matchingColor) 3 else 1
                wordMultiplier *= premium.wordMultiplier
            } else {
                // Płytki położone wcześniej nie uruchamiają premii ponownie.
                lettersTotal += points
            }
        }
        return lettersTotal * wordMultiplier
    }

    private fun collectCrossWord(
        board: BoardState,
        placed: PlacedTile,
        newTiles: Map<Pair<Int, Int>, PlacedTile>,
        mainHorizontal: Boolean
    ): List<Pair<Int, Int>> {
        val dr = if (mainHorizontal) 1 else 0
        val dc = if (mainHorizontal) 0 else 1
        var row = placed.row
        var column = placed.column

        while (inside(row - dr, column - dc) &&
            tileAt(board, newTiles, row - dr, column - dc) != null
        ) {
            row -= dr
            column -= dc
        }

        val result = mutableListOf<Pair<Int, Int>>()
        while (inside(row, column) &&
            tileAt(board, newTiles, row, column) != null
        ) {
            result += row to column
            row += dr
            column += dc
        }
        return result
    }

    private fun tileAt(
        board: BoardState,
        newTiles: Map<Pair<Int, Int>, PlacedTile>,
        row: Int,
        column: Int
    ): BoardTile? = newTiles[row to column]?.tile ?: board.tileAt(row, column)

    private fun tileColorPoints(tile: BoardTile): Int =
        TileColor.forLetter(tile.letter, tile.isBlank).points

    private fun inside(row: Int, column: Int): Boolean =
        row in 0 until BoardState.SIZE && column in 0 until BoardState.SIZE

    companion object {
        const val SEVEN_TILE_BONUS = 50
    }
}
