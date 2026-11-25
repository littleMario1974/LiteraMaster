package com.littleMario.litera

/**
 * Podsumowanie kolorów wyłącznie tych pól, dla których OCR rozpoznał literę.
 * Biała płytka jest kandydatem na blank, ale nie jest automatycznie blankiem.
 */
data class BoardTileColorDiagnostics(
    val colorCounts: Map<TileColor?, Int>,
    val suspectedBlankPositions: Set<Pair<Int, Int>>
) {
    companion object {
        fun from(results: List<BoardCellOcrResult>): BoardTileColorDiagnostics {
            val occupied = results.filter { it.letter != null }
            return BoardTileColorDiagnostics(
                colorCounts = occupied.groupingBy { it.tileColor }.eachCount(),
                suspectedBlankPositions = occupied
                    .filter { it.tileColor == TileColor.WHITE }
                    .map { it.row to it.column }
                    .toSet()
            )
        }
    }
}
