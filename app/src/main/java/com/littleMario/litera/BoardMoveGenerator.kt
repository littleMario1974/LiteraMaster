package com.littleMario.litera

/**
 * Generuje legalne ruchy na planszy na podstawie DAWG i stojaka.
 *
 * Nie oblicza punktów: układ pól premiowych Literaków musi zostać
 * najpierw odwzorowany z planszy używanej przez gracza.
 */
class BoardMoveGenerator(
    private val nodes: Array<Node>,
    private val rootId: Int = 0
) {
    private val alphabet = DawgWordFinder.ALPHABET

    init {
        require(nodes.isNotEmpty()) { "Słownik DAWG jest pusty." }
        require(rootId in nodes.indices) { "Nieprawidłowy korzeń DAWG." }
    }

    fun generate(board: BoardState, rack: LetterRack): List<BoardMove> {
        if (rack.size == 0) return emptyList()

        // Rack composition is constant for the whole generation pass.
        // Reuse one counter array and restore its initial contents before each
        // start position instead of allocating a fresh array hundreds of times.
        val initialCounts = IntArray(alphabet.length + 1)
        rack.letters.forEach { letter ->
            if (letter == '?') initialCounts[alphabet.length]++
            else {
                val index = alphabet.indexOf(letter.lowercaseChar())
                require(index >= 0) { "Litera $letter nie występuje w alfabecie DAWG." }
                initialCounts[index]++
            }
        }
        // Avoid building a PlacedTile list just to test whether the board is empty.
        val boardHasTiles = board.hasTiles()
        val counts = IntArray(initialCounts.size)
        val results = linkedMapOf<String, BoardMove>()
        for (horizontal in listOf(true, false)) {
            for (row in 0 until BoardState.SIZE) {
                for (column in 0 until BoardState.SIZE) {
                    val previousRow = if (horizontal) row else row - 1
                    val previousColumn = if (horizontal) column - 1 else column
                    if (inside(previousRow, previousColumn) &&
                        board.tileAt(previousRow, previousColumn) != null
                    ) continue

                    initialCounts.copyInto(counts)

                    search(
                        board = board,
                        row = row,
                        column = column,
                        startRow = row,
                        startColumn = column,
                        horizontal = horizontal,
                        nodeId = rootId,
                        counts = counts,
                        path = StringBuilder(),
                        placements = mutableListOf(),
                        results = results,
                        boardHasTiles = boardHasTiles
                    )
                }
            }
        }

        return results.values.sortedWith(
            compareByDescending<BoardMove> { it.placements.size }
                .thenByDescending { it.word.length }
                .thenBy { it.word }
                .thenBy { it.row }
                .thenBy { it.column }
                .thenBy { it.horizontal }
        )
    }

    private fun search(
        board: BoardState,
        row: Int,
        column: Int,
        startRow: Int,
        startColumn: Int,
        horizontal: Boolean,
        nodeId: Int,
        counts: IntArray,
        path: StringBuilder,
        placements: MutableList<PlacedTile>,
        results: MutableMap<String, BoardMove>,
        boardHasTiles: Boolean
    ) {
        if (!inside(row, column) || path.length >= BoardState.SIZE) return

        val existing = board.tileAt(row, column)
        val nextRow = row + if (horizontal) 0 else 1
        val nextColumn = column + if (horizontal) 1 else 0

        if (existing != null) {
            val index = alphabet.indexOf(existing.normalizedLetter.lowercaseChar())
            if (index < 0) return
            val child = nodes[nodeId].next[index]
            if (child == -1) return

            path.append(alphabet[index])
            recordIfComplete(board, nextRow, nextColumn, startRow, startColumn, horizontal, child, path, placements, results, boardHasTiles)
            search(board, nextRow, nextColumn, startRow, startColumn, horizontal, child, counts, path, placements, results, boardHasTiles)
            path.deleteCharAt(path.length - 1)
            return
        }

        val node = nodes[nodeId]
        for (index in alphabet.indices) {
            val child = node.next[index]
            if (child == -1) continue

            // Explore a normal rack tile and a blank directly. Building a
            // temporary list of Pair values here would allocate at every
            // recursive DAWG node.
            if (counts[index] > 0) {
                explorePlacement(
                    board, row, column, startRow, startColumn, horizontal,
                    child, index, index, false, counts, path, placements, results, boardHasTiles
                )
            }
            val blankIndex = alphabet.length
            if (counts[blankIndex] > 0) {
                explorePlacement(
                    board, row, column, startRow, startColumn, horizontal,
                    child, index, blankIndex, true, counts, path, placements, results, boardHasTiles
                )
            }
        }
    }

    private fun explorePlacement(
        board: BoardState,
        row: Int,
        column: Int,
        startRow: Int,
        startColumn: Int,
        horizontal: Boolean,
        child: Int,
        letterIndex: Int,
        countIndex: Int,
        usesBlank: Boolean,
        counts: IntArray,
        path: StringBuilder,
        placements: MutableList<PlacedTile>,
        results: MutableMap<String, BoardMove>,
        boardHasTiles: Boolean
    ) {
        counts[countIndex]--
        val placedLetter = alphabet[letterIndex]
        path.append(placedLetter)
        placements.add(
            PlacedTile(
                row,
                column,
                BoardTile(placedLetter.uppercaseChar(), isBlank = usesBlank)
            )
        )

        val nextRow = row + if (horizontal) 0 else 1
        val nextColumn = column + if (horizontal) 1 else 0
        recordIfComplete(
            board, nextRow, nextColumn, startRow, startColumn, horizontal,
            child, path, placements, results, boardHasTiles
        )
        search(
            board, nextRow, nextColumn, startRow, startColumn, horizontal,
            child, counts, path, placements, results, boardHasTiles
        )

        placements.removeAt(placements.lastIndex)
        path.deleteCharAt(path.length - 1)
        counts[countIndex]++
    }

    private fun recordIfComplete(
        board: BoardState,
        nextRow: Int,
        nextColumn: Int,
        startRow: Int,
        startColumn: Int,
        horizontal: Boolean,
        nodeId: Int,
        path: StringBuilder,
        placements: List<PlacedTile>,
        results: MutableMap<String, BoardMove>,
        boardHasTiles: Boolean
    ) {
        if (!nodes[nodeId].terminal || placements.isEmpty()) return
        if (inside(nextRow, nextColumn) && board.tileAt(nextRow, nextColumn) != null) return

        val coversCenter = if (horizontal) {
            startRow == 7 && 7 in (startColumn until (startColumn + path.length))
        } else {
            startColumn == 7 && 7 in (startRow until (startRow + path.length))
        }
        var touchesBoard = false
        if (boardHasTiles) {
            for (placed in placements) {
                val row = placed.row
                val column = placed.column
                if ((inside(row - 1, column) && board.tileAt(row - 1, column) != null) ||
                    (inside(row + 1, column) && board.tileAt(row + 1, column) != null) ||
                    (inside(row, column - 1) && board.tileAt(row, column - 1) != null) ||
                    (inside(row, column + 1) && board.tileAt(row, column + 1) != null)
                ) {
                    touchesBoard = true
                    break
                }
            }
        }
        if (!boardHasTiles && !coversCenter) return
        if (boardHasTiles && !touchesBoard) return

        if (!crossWordsAreValid(board, placements, horizontal)) return

        // Preserve blank positions in placement order without temporary lists.
        val blankPattern = StringBuilder()
        for (placed in placements) {
            if (!placed.tile.isBlank) continue
            if (blankPattern.isNotEmpty()) blankPattern.append(',')
            blankPattern.append(placed.row).append(':').append(placed.column)
        }
        val key = "${if (horizontal) 'H' else 'V'}:$startRow:$startColumn:$path:$blankPattern"
        // A move needs a snapshot because placements is reused during recursion.
        // Avoid creating that snapshot when an equivalent move was already found.
        if (!results.containsKey(key)) {
            results[key] = BoardMove(path.toString(), startRow, startColumn, horizontal, placements.toList())
        }
    }

    private fun crossWordsAreValid(
        board: BoardState,
        placements: List<PlacedTile>,
        horizontal: Boolean
    ): Boolean {
        // All newly placed tiles lie on the main word's line. A perpendicular
        // cross word can therefore contain only one new tile: the current one.
        // Read the existing board directly instead of repeatedly searching the
        // placements list for every cell in every cross word.
        val dr = if ( horizontal) 1 else 0
        val dc = if (horizontal) 0 else 1

        for (placed in placements) {
            var startRow = placed.row
            var startColumn = placed.column
            while (inside(startRow - dr, startColumn - dc) &&
                board.tileAt(startRow - dr, startColumn - dc) != null
            ) {
                startRow -= dr
                startColumn -= dc
            }

            val word = StringBuilder()
            var row = startRow
            var column = startColumn
            while (inside(row, column)) {
                val tile = if (row == placed.row && column == placed.column) {
                    placed.tile
                } else {
                    board.tileAt(row, column)
                } ?: break
                word.append(tile.normalizedLetter.lowercaseChar())
                row += dr
                column += dc
            }
            if (word.length > 1 && !containsWord(word.toString())) return false
        }
        return true
    }

    private fun containsWord(word: String): Boolean {
        var nodeId = rootId
        for (char in word) {
            val index = alphabet.indexOf(char)
            if (index < 0) return false
            nodeId = nodes[nodeId].next[index]
            if (nodeId == -1) return false
            require(nodeId in nodes.indices) { "Słownik zawiera nieprawidłowe przejście." }
        }
        return nodes[nodeId].terminal
    }

    private fun inside(row: Int, column: Int): Boolean =
        row in 0 until BoardState.SIZE && column in 0 until BoardState.SIZE
}

data class BoardMove(
    val word: String,
    val row: Int,
    val column: Int,
    val horizontal: Boolean,
    val placements: List<PlacedTile>
)
