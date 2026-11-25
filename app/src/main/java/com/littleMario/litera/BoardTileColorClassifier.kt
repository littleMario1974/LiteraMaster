package com.littleMario.litera

import kotlin.math.max
import kotlin.math.min

/**
 * Ostrożna klasyfikacja koloru powierzchni płytki ze średniej RGB.
 *
 * Zwraca null, gdy barwa jest zbyt mało nasycona lub nie pasuje
 * do znanych grup kolorów. WHITE oznacza prawdopodobny biały blank.
 * Progi są wstępne i wymagają sprawdzenia na zdjęciach konkretnego zestawu.
 */
object BoardTileColorClassifier {

    fun classify(red: Int, green: Int, blue: Int): TileColor? {
        require(red in 0..255 && green in 0..255 && blue in 0..255)

        val r = red / 255.0
        val g = green / 255.0
        val b = blue / 255.0
        val maximum = max(r, max(g, b))
        val minimum = min(r, min(g, b))
        val delta = maximum - minimum
        val saturation = if (maximum == 0.0) 0.0 else delta / maximum
        val brightness = maximum

        if (brightness >= 0.72 && saturation <= 0.16) {
            return TileColor.WHITE
        }
        if (saturation < 0.28 || brightness < 0.25) return null

        var hue = when {
            delta == 0.0 -> return null
            maximum == r -> 60.0 * (((g - b) / delta) % 6.0)
            maximum == g -> 60.0 * (((b - r) / delta) + 2.0)
            else -> 60.0 * (((r - g) / delta) + 4.0)
        }
        if (hue < 0.0) hue += 360.0

        return when {
            hue < 18.0 || hue >= 345.0 -> TileColor.RED
            hue in 35.0..70.0 -> TileColor.YELLOW
            hue in 75.0..170.0 -> TileColor.GREEN
            hue in 190.0..270.0 -> TileColor.BLUE
            else -> null
        }
    }
}
