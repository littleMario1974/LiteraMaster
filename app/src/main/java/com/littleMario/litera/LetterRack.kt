package com.littleMario.litera

/**
 * Stojak gracza. Znak '?' oznacza blank (dowolną literę).
 * W Literakach gracz ma maksymalnie siedem płytek.
 */
data class LetterRack private constructor(
    val letters: List<Char>
) {
    val size: Int
        get() = letters.size

    val blankCount: Int
        get() = letters.count { it == '?' }

    fun count(letter: Char): Int =
        letters.count { it == letter.uppercaseChar() }

    companion object {
        const val MAX_TILES = 7
        const val POLISH_ALPHABET = "AĄBCĆDEĘFGHIJKLŁMNŃOÓPRSŚTUWYZŹŻ"

        /**
         * Zwraca null, jeśli stojak zawiera niedozwolony znak
         * albo więcej niż siedem płytek.
         */
        fun parse(input: String): LetterRack? {
            val normalized = input
                .filterNot { it.isWhitespace() }
                .uppercase()

            if (normalized.length > MAX_TILES) return null
            if (normalized.any { it != '?' && it !in POLISH_ALPHABET }) {
                return null
            }

            return LetterRack(normalized.toList())
        }
    }
}
