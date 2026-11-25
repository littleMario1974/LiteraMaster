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
        // Copy it per start position because recursive search mutates the counters.
        val initialCounts = IntArray(alphabet.length + 1)
        rack.letters.forEach { letter ->
            if (letter == '?') initialCounts[alphabet.length]++
            else {
                val index = alphabet.indexOf(letter.lowercaseChar())
                require(index >= 0) { "Litera $letter nie występuje w alfabecie DAWG." }
                initialCounts[index]++
            }
        }
        val boardHasTiles = board.occupiedTiles().isNotEmpty()
        val results = linkedMapOf<String, BoardMove>()
        for (horizontal in listOf(true, false)) {
            for (row in 0 until BoardState.SIZE) {
                for (column in 0 until BoardState.SIZE) {
                    val previousRow = if (horizontal) row else row - 1
                    val previousColumn = if (horizontal) column - 1 else column
                    if (inside(previousRow, previousColumn) &&
                        board.tileAt(previousRow, previousColumn) != null
                    ) continue

                    val counts = initialCounts.copyOf()

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
        val touchesBoard = boardHasTiles &&
            placements.any { placed ->
                ORTHOGONAL_NEIGHBORS.any { (dr, dc) ->
                    val r = placed.row + dr
                    val c = placed.column + dc
                    inside(r, c) && board.tileAt(r, c) != null
                }
            }
        if (!boardHasTiles && !coversCenter) return
        if (boardHasTiles && !touchesBoard) return

        if (!crossWordsAreValid(board, placements, horizontal)) return

        val blankPattern = placements.filter { it.tile.isBlank }
            .joinToString(",") { "${it.row}:${it.column}" }
        val key = "${if (horizontal) 'H' else 'V'}:$startRow:$startColumn:$path:$blankPattern"
        results.putIfAbsent(
            key,
            BoardMove(path.toString(), startRow, startColumn, horizontal, placements.toList())
        )
    }

    private fun crossWordsAreValid(
        board: BoardState,
        placements: List<PlacedTile>,
        horizontal: Boolean
    ): Boolean {
        for (placed in placements) {
            val dr = if (horizontal) 1 else 0
            val dc = if (horizontal) 0 else 1
            var startRow = placed.row
            var startColumn = placed.column
            while (inside(startRow - dr, startColumn - dc) &&
                tileAtWithPlacements(board, placements, startRow - dr, startColumn - dc) != null
            ) {
                startRow -= dr
                startColumn -= dc
            }

            val word = StringBuilder()
            var r = startRow
            var c = startColumn
            while (inside(r, c)) {
                val tile = tileAtWithPlacements(board, placements, r, c) ?: break
                word.append(tile.normalizedLetter.lowercaseChar())
                r += dr
                c += dc
            }
            if (word.length > 1 && !containsWord(word.toString())) return false
        }
        return true
    }

    private fun tileAtWithPlacements(
        board: BoardState,
        placements: List<PlacedTile>,
        row: Int,
        column: Int
    ): BoardTile? = placements.firstOrNull { it.row == row && it.column == column }?.tile
        ?: board.tileAt(row, column)

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

    companion object {
        private val ORTHOGONAL_NEIGHBORS = listOf(
            -1 to 0, 1 to 0, 0 to -1, 0 to 1
        )
    }
}

data class BoardMove(
    val word: String,
    val row: Int,
    val column: Int,
    val horizontal: Boolean,
    val placements: List<PlacedTile>
)
