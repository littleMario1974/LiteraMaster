package com.littleMario.litera

/**
 * Oblicza pewność OCR na podstawie elementów zawierających rozpoznaną literę.
 * Elementy zawierające wyłącznie cyfry punktacji nie wpływają na wynik.
 */
object BoardOcrConfidence {

    fun calculate(elements: List<Pair<String, Float?>>): Float? {
        val letterConfidences = elements.mapNotNull { (text, confidence) ->
            confidence?.takeIf { BoardTileTextParser.parse(text) != null }
        }
        return letterConfidences.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    }
}
