package com.littleMario.litera

/**
 * Zamienia wynik OCR pojedynczego pola na literę alfabetu polskiego.
 *
 * Cyfry punktacji i inne znaki są pomijane. Gdy OCR nie zwróci żadnej
 * litery albo zwróci więcej niż jedną, wynik pozostaje nierozstrzygnięty.
 */
object BoardTileTextParser {
    private const val POLISH_LETTERS = "AĄBCĆDEĘFGHIJKLŁMNŃOÓPRSŚTUWYZŹŻ"

    fun parse(text: String): Char? {
        val letters = text.uppercase()
            .filter { it in POLISH_LETTERS }

        return letters.singleOrNull()
    }
}
