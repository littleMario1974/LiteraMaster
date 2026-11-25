package com.littleMario.litera

/**
 * Wyszukuje słowa możliwe do ułożenia z płytek stojaka, korzystając
 * z istniejącego pliku dictionary.dawg.
 *
 * Indeksy przejść muszą odpowiadać alfabetowi użytemu przy budowie DAWG
 * (tak samo jak w dotychczasowym solverze MainActivity).
 */
class DawgWordFinder(
    private val nodes: Array<Node>,
    private val rootId: Int = 0
) {
    init {
        require(nodes.isNotEmpty()) { "Słownik DAWG jest pusty." }
        require(rootId in nodes.indices) { "Nieprawidłowy korzeń DAWG." }
    }

    fun findWords(rack: LetterRack, maxLength: Int = LetterRack.MAX_TILES): List<String> {
        require(maxLength >= 1) { "Maksymalna długość musi wynosić co najmniej 1." }

        val counts = IntArray(ALPHABET.length + 1)
        for (letter in rack.letters) {
            if (letter == '?') {
                counts[ALPHABET.length]++
            } else {
                val index = ALPHABET.indexOf(letter.lowercaseChar())
                require(index >= 0) { "Litera $letter nie występuje w alfabecie DAWG." }
                counts[index]++
            }
        }

        val found = linkedSetOf<String>()
        search(rootId, StringBuilder(), counts, maxLength, found)
        return found.sortedWith(compareByDescending<String> { it.length }.thenBy { it })
    }

    private fun search(
        nodeId: Int,
        path: StringBuilder,
        counts: IntArray,
        maxLength: Int,
        found: MutableSet<String>
    ) {
        if (path.length >= maxLength) {
            if (nodes[nodeId].terminal) found.add(path.toString())
            return
        }

        val node = nodes[nodeId]
        if (node.terminal) found.add(path.toString())

        for (index in ALPHABET.indices) {
            val childId = node.next[index]
            if (childId == -1) continue
            require(childId in nodes.indices) { "Słownik zawiera nieprawidłowe przejście: $childId." }

            val letter = ALPHABET[index]
            if (counts[index] > 0) {
                counts[index]--
                path.append(letter)
                search(childId, path, counts, maxLength, found)
                path.deleteCharAt(path.length - 1)
                counts[index]++
            } else if (counts[ALPHABET.length] > 0) {
                counts[ALPHABET.length]--
                path.append(letter)
                search(childId, path, counts, maxLength, found)
                path.deleteCharAt(path.length - 1)
                counts[ALPHABET.length]++
            }
        }
    }

    companion object {
        // Kolejność znaków musi pozostać identyczna jak w MainActivity.
        const val ALPHABET = "aąbcćdeęfghijklłmnńoópqrsśtuvwxyzźż"
    }
}
