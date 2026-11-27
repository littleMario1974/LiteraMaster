package com.littleMario.litera

/**
 * Zamienia wynik OCR pojedynczego pola na literę alfabetu polskiego.
 *
 * Odrzuca cały odczyt, jeśli zawiera dodatkowy znak. To istotne dla plansz
 * z nadrukami pól premiowych, np. "2L" lub "3S": wcześniejsze filtrowanie
 * cyfr pozostawiało z nich samo L/S i błędnie traktowało nadruk jako płytkę.
 */
object BoardTileTextParser {
    private const val POLISH_LETTERS = "AĄBCĆDEĘFGHIJKLŁMNŃOÓPRSŚTUWYZŹŻ"

    fun parse(text: String): Char? {
        val normalized = text.trim().uppercase()
        if (normalized.length != 1) return null
        return normalized.single().takeIf { it in POLISH_LETTERS }
    }
}
