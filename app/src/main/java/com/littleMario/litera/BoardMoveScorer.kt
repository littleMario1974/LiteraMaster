package com.littleMario.litera

/**
 * Premie pojedynczego pola planszy.
 *
 * W Literakach premia literowa wynika z dopasowania koloru płytki
 * do koloru pola. Layout planszy powinien więc przekazać mnożnik 3
 * tylko dla pól, na których kolor rzeczywiście pasuje.
 */
data class BoardPremium(
    val letterMultiplier: Int = 1,
    val wordMultiplier: Int = 1
) {
    init {
        require(letterMultiplier >= 1)
        require(wordMultiplier >= 1)
    }

    companion object {
        val NONE = BoardPremium()
    }
}

/**
 * Liczy punkty za ruch. Premie są naliczane wyłącznie nowym płytkom.
 * Każde słowo poprzeczne utworzone ruchem jest punktowane osobno.
 */
class BoardMoveScorer(
    private val premiumAt: (row: Int, column: Int) -> BoardPremium = { _, _ -> BoardPremium.NONE }
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

        // Każda dokładana płytka może utworzyć jedno słowo w poprzek.
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
            val points = tilePoints(tile)
            if (newlyPlaced != null) {
                val premium = premiumAt(row, column)
                lettersTotal += points * premium.letterMultiplier
                wordMultiplier *= premium.wordMultiplier
            } else {
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
    ): BoardTile? = newTiles[row to column]?.tile
        ?: board.tileAt(row, column)

    private fun tilePoints(tile: BoardTile): Int {
        if (tile.isBlank) return 0
        return when (tile.normalizedLetter) {
            'A', 'E', 'I', 'N', 'O', 'R', 'S', 'W', 'Z' -> 1
            'C', 'D', 'K', 'L', 'M', 'P', 'T', 'Y' -> 2
            'B', 'G', 'H', 'J', 'Ł', 'U' -> 3
            'Ą', 'Ć', 'Ę', 'F', 'Ń', 'Ó', 'Ś', 'Ź', 'Ż' -> 5
            else -> error("Nieznana litera w zestawie Literaków: ${tile.letter}")
        }
    }

    private fun inside(row: Int, column: Int): Boolean =
        row in 0 until BoardState.SIZE && column in 0 until BoardState.SIZE

    companion object {
        const val SEVEN_TILE_BONUS = 50
    }
}
