package com.littleMario.litera

import android.graphics.Bitmap

/**
 * Wyodrębnia z fotografii obrazy poszczególnych pól planszy.
 *
 * W tej wersji narożniki planszy przekazuje użytkownik lub wcześniejszy
 * etap wykrywania planszy. Automatyczne wykrywanie narożników i OCR
 * zostaną dodane osobno.
 */
class BoardCellImageExtractor(
    private val outputCellSize: Int = DEFAULT_OUTPUT_CELL_SIZE,
    private val insetFraction: Double = DEFAULT_INSET_FRACTION
) {
    init {
        require(outputCellSize > 0) { "Rozmiar obrazu pola musi być dodatni." }
        require(insetFraction >= 0.0 && insetFraction < 0.5) {
            "Margines pola musi należeć do zakresu 0.0..0.5."
        }
    }

    /**
     * Zwraca obrazy wszystkich 225 pól w kolejności wierszami:
     * najpierw wiersz 0 od kolumny 0 do 14, następnie wiersz 1 itd.
     *
     * Niewielki margines odcina linie siatki i krawędzie kafelków.
     */
    fun extractCells(
        source: Bitmap,
        geometry: BoardGridGeometry
    ): List<Bitmap> {
        require(!source.isRecycled) { "Obraz źródłowy został zwolniony." }

        val cells = ArrayList<Bitmap>(BoardState.SIZE * BoardState.SIZE)
        try {
            for (row in 0 until BoardState.SIZE) {
                for (column in 0 until BoardState.SIZE) {
                    cells.add(extractCell(source, geometry, row, column))
                }
            }
            return cells
        } catch (exception: Exception) {
            // If extraction fails midway, release every bitmap already created.
            cells.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            throw exception
        }
    }

    fun extractCell(
        source: Bitmap,
        geometry: BoardGridGeometry,
        row: Int,
        column: Int
    ): Bitmap {
        require(!source.isRecycled) { "Obraz źródłowy został zwolniony." }

        val left = column.toDouble() / BoardState.SIZE
        val top = row.toDouble() / BoardState.SIZE
        val cellWidth = 1.0 / BoardState.SIZE
        val cellHeight = 1.0 / BoardState.SIZE

        val startU = left + cellWidth * insetFraction
        val endU = left + cellWidth * (1.0 - insetFraction)
        val startV = top + cellHeight * insetFraction
        val endV = top + cellHeight * (1.0 - insetFraction)

        val output = Bitmap.createBitmap(
            outputCellSize,
            outputCellSize,
            Bitmap.Config.ARGB_8888
        )

        try {
            val pixels = IntArray(outputCellSize * outputCellSize)

            for (y in 0 until outputCellSize) {
                val vFraction = if (outputCellSize == 1) 0.5
                else y.toDouble() / (outputCellSize - 1)
                val v = startV + (endV - startV) * vFraction

                for (x in 0 until outputCellSize) {
                    val uFraction = if (outputCellSize == 1) 0.5
                    else x.toDouble() / (outputCellSize - 1)
                    val u = startU + (endU - startU) * uFraction

                    val point = geometry.pointAt(u, v)
                    val sourceX = point.x.toInt().coerceIn(0, source.width - 1)
                    val sourceY = point.y.toInt().coerceIn(0, source.height - 1)

                    pixels[y * outputCellSize + x] =
                        source.getPixel(sourceX, sourceY)
                }
            }

            output.setPixels(
                pixels,
                0,
                outputCellSize,
                0,
                0,
                outputCellSize,
                outputCellSize
            )
            return output
        } catch (exception: Exception) {
            // Do not leave a native bitmap allocated if sampling or setPixels fails.
            output.recycle()
            throw exception
        }
    }

    companion object {
        const val DEFAULT_OUTPUT_CELL_SIZE = 96
        const val DEFAULT_INSET_FRACTION = 0.12
    }
}
