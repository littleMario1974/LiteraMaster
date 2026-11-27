package com.littleMario.litera

/**
 * Zamienia wynik OCR pojedynczego pola na literę alfabetu polskiego.
 *
 * Dopuszcza cyfrę punktacji po literze (np. "Ż 10"), ale nie usuwa cyfr
 * z początku odczytu. Dzięki temu nadruki pól premiowych "2L"/"3S" nie
 * zamieniają się błędnie w litery L/S.
 */
object BoardTileTextParser {
    private const val POLISH_LETTERS = "AĄBCĆDEĘFGHIJKLŁMNŃOÓPRSŚTUWYZŹŻ"

    fun parse(text: String): Char? {
        val normalized = text.trim().uppercase()
        if (normalized.isEmpty()) return null

        val letter = normalized.first()
        if (letter !in POLISH_LETTERS) return null

        val suffix = normalized.drop(1).filterNot { it.isWhitespace() }
        if (suffix.any { !it.isDigit() }) return null

        return letter
    }
}
