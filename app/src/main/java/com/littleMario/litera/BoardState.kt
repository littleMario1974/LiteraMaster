package com.littleMario.litera

/**
 * Model planszy Literaków.
 *
 * Współrzędne są liczone od lewego górnego rogu:
 * row = 0..14 (wiersze), column = 0..14 (kolumny).
 *
 * Klasa nie zakłada jeszcze rozmieszczenia pól premiowych ani sposobu
 * rozpoznawania obrazu. Dzięki temu model pozostaje niezależny od kamery
 * i może być używany również przez ręczny edytor planszy.
 */
data class BoardTile(
    val letter: Char,
    val isBlank: Boolean = false
) {
    init {
        require(letter.isLetter()) { "Litera musi być znakiem alfabetu." }
    }

    val normalizedLetter: Char
        get() = letter.uppercaseChar()
}

data class BoardState(
    val cells: List<List<BoardTile?>>
) {
    init {
        require(cells.size == SIZE) {
            "Plansza musi mieć dokładnie $SIZE wierszy."
        }
        require(cells.all { it.size == SIZE }) {
            "Każdy wiersz planszy musi mieć dokładnie $SIZE pól."
        }
    }

    fun tileAt(row: Int, column: Int): BoardTile? {
        requireValidPosition(row, column)
        return cells[row][column]
    }

    fun withTile(row: Int, column: Int, tile: BoardTile?): BoardState {
        requireValidPosition(row, column)

        val updatedRows = cells.mapIndexed { rowIndex, currentRow ->
            if (rowIndex == row) {
                currentRow.toMutableList().also { it[column] = tile }
            } else {
                currentRow
            }
        }

        return BoardState(updatedRows)
    }

    /**
     * Checks whether the board contains any tile without allocating
     * the PlacedTile list returned by [occupiedTiles].
     */
    fun hasTiles(): Boolean {
        for (row in cells) {
            for (tile in row) {
                if (tile != null) return true
            }
        }
        return false
    }

    fun occupiedTiles(): List<PlacedTile> =
        buildList {
            cells.forEachIndexed { row, line ->
                line.forEachIndexed { column, tile ->
                    if (tile != null) {
                        add(PlacedTile(row, column, tile))
                    }
                }
            }
        }

    private fun requireValidPosition(row: Int, column: Int) {
        require(row in 0 until SIZE) { "Nieprawidłowy wiersz: $row" }
        require(column in 0 until SIZE) { "Nieprawidłowa kolumna: $column" }
    }

    companion object {
        const val SIZE = 15

        fun empty(): BoardState =
            BoardState(List(SIZE) { List<BoardTile?>(SIZE) { null } })
    }
}

data class PlacedTile(
    val row: Int,
    val column: Int,
    val tile: BoardTile
)
