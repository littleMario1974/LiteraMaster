package com.littleMario.litera

/**
 * Wynik analizy pojedynczego zdjęcia planszy.
 *
 * Puste OCR nie jest traktowane jako pewny dowód pustego pola:
 * litera mogła zostać pominięta przez rozpoznawanie.
 */
data class BoardPhotoAnalysis(
    val geometry: BoardGridGeometry,
    val cellResults: List<BoardCellOcrResult>,
    val recognizedBoard: BoardState,
    val unresolvedCells: List<BoardCellOcrResult>
) {
    val recognizedTileCount: Int
        get() = recognizedBoard.occupiedTiles().size

    val unresolvedCount: Int
        get() = unresolvedCells.size
}

/**
 * Klasyfikuje wyniki OCR i składa z rozpoznanych liter model planszy.
 * Klasa nie zależy od Androida, dlatego można ją testować jednostkowo.
 */
object BoardPhotoAnalysisMapper {

    fun map(
        geometry: BoardGridGeometry,
        cellResults: List<BoardCellOcrResult>
    ): BoardPhotoAnalysis {
        require(cellResults.size == BoardState.SIZE * BoardState.SIZE) {
            "Analiza musi zawierać wyniki dla dokładnie 225 pól."
        }

        val positions = cellResults.map { it.row to it.column }
        require(positions.toSet().size == cellResults.size) {
            "Wyniki OCR zawierają powtórzone współrzędne pól."
        }
        require(positions.all { (row, column) ->
            row in 0 until BoardState.SIZE && column in 0 until BoardState.SIZE
        }) {
            "Wynik OCR zawiera współrzędne spoza planszy."
        }

        val byPosition = cellResults.associateBy { it.row to it.column }
        require(byPosition.size == BoardState.SIZE * BoardState.SIZE) {
            "Wyniki OCR nie obejmują wszystkich pól planszy."
        }

        var board = BoardState.empty()
        val unresolved = mutableListOf<BoardCellOcrResult>()

        for (row in 0 until BoardState.SIZE) {
            for (column in 0 until BoardState.SIZE) {
                val result = byPosition.getValue(row to column)
                val letter = result.letter

                if (letter != null) {
                    board = board.withTile(row, column, BoardTile(letter, isBlank = result.tileColor == TileColor.WHITE))
                } else {
                    // Zarówno pusty tekst, jak i niejednoznaczny tekst
                    // pozostawiamy do ewentualnej weryfikacji.
                    unresolved += result
                }
            }
        }

        return BoardPhotoAnalysis(
            geometry = geometry,
            cellResults = cellResults.sortedWith(
                compareBy<BoardCellOcrResult> { it.row }.thenBy { it.column }
            ),
            recognizedBoard = board,
            unresolvedCells = unresolved
        )
    }
}
