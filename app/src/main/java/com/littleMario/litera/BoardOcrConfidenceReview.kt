package com.littleMario.litera

/**
 * Wskazuje litery rozpoznane z niską pewnością, które warto sprawdzić ręcznie.
 * Brak confidence nie jest traktowany jako niski wynik — ML Kit może go nie podać.
 */
object BoardOcrConfidenceReview {
    const val MIN_CONFIDENCE = 0.65f

    fun lowConfidencePositions(
        results: List<BoardCellOcrResult>
    ): Set<Pair<Int, Int>> =
        results.asSequence()
            .filter { it.letter != null }
            .filter { it.confidence != null && it.confidence < MIN_CONFIDENCE }
            .map { it.row to it.column }
            .toSet()
}
