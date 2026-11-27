package com.littleMario.litera

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Odróżnia dużą, jednolitą płytkę od małego nadruku pola premiowego.
 *
 * Wycięte pole ma już niewielki margines (BoardCellImageExtractor).
 * Płytka zajmuje prawie całą jego szerokość i wysokość, natomiast
 * nadruk 2x/3x jest wyraźnie mniejszy. Sprawdzamy zasięg barwnych
 * pikseli, a nie samą średnią barwę, dzięki czemu kolorowe pola
 * premiowe nie są przekazywane do OCR jako potencjalne litery.
 */
object BoardTilePresenceDetector {

    fun isLikelyTile(bitmap: Bitmap): Boolean {
        require(!bitmap.isRecycled) { "Obraz pola został zwolniony." }
        if (bitmap.width < 8 || bitmap.height < 8) return false

        val hsv = FloatArray(3)
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = -1
        var maxY = -1
        var coloredPixels = 0

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                // Odrzucamy szarą kratkę i czarny nadruk planszy.
                if (hsv[1] >= MIN_SATURATION && hsv[2] >= MIN_BRIGHTNESS) {
                    coloredPixels++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (coloredPixels == 0) return false

        val widthSpan = (maxX - minX + 1).toFloat() / bitmap.width
        val heightSpan = (maxY - minY + 1).toFloat() / bitmap.height
        val coverage = coloredPixels.toFloat() / (bitmap.width * bitmap.height)

        return widthSpan >= MIN_SPAN &&
            heightSpan >= MIN_SPAN &&
            coverage >= MIN_COLORED_COVERAGE
    }

    private const val MIN_SATURATION = 0.24f
    private const val MIN_BRIGHTNESS = 0.20f
    private const val MIN_SPAN = 0.84f
    private const val MIN_COLORED_COVERAGE = 0.30f
}
