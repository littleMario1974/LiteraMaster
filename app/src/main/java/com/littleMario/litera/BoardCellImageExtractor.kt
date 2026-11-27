package com.littleMario.litera

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Wyodrębnia 225 pól z obrazu po pełnej korekcji perspektywy.
 *
 * Najpierw przekształca cały czworokąt planszy do kwadratu 15 x 15 pól
 * za pomocą homografii. Dopiero z tak wyprostowanego obrazu wycina komórki.
 * Dzięki temu każda kratka ma stałe położenie i rozmiar także na zdjęciu
 * wykonanym pod kątem.
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
     * Zwraca obrazy 225 pól w kolejności wierszami.
     * Bitmapa źródłowa pozostaje własnością wywołującego.
     */
    fun extractCells(
        source: Bitmap,
        geometry: BoardGridGeometry
    ): List<Bitmap> {
        require(!source.isRecycled) { "Obraz źródłowy został zwolniony." }
        check(OpenCVLoader.initLocal()) {
            "Nie udało się uruchomić OpenCV podczas prostowania planszy."
        }

        val boardPixels = BoardState.SIZE * RECTIFIED_CELL_SIZE
        val sourceMat = Mat()
        val rectifiedMat = Mat()
        val transform = Mat()
        val sourceCorners = MatOfPoint2f(
            Point(geometry.topLeft.x, geometry.topLeft.y),
            Point(geometry.topRight.x, geometry.topRight.y),
            Point(geometry.bottomRight.x, geometry.bottomRight.y),
            Point(geometry.bottomLeft.x, geometry.bottomLeft.y)
        )
        val targetCorners = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((boardPixels - 1).toDouble(), 0.0),
            Point((boardPixels - 1).toDouble(), (boardPixels - 1).toDouble()),
            Point(0.0, (boardPixels - 1).toDouble())
        )
        var rectifiedBitmap: Bitmap? = null

        try {
            Utils.bitmapToMat(source, sourceMat)
            Imgproc.getPerspectiveTransform(sourceCorners, targetCorners, transform)
            Imgproc.warpPerspective(
                sourceMat,
                rectifiedMat,
                transform,
                Size(boardPixels.toDouble(), boardPixels.toDouble()),
                Imgproc.INTER_CUBIC
            )
            rectifiedBitmap = Bitmap.createBitmap(
                boardPixels, boardPixels, Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(rectifiedMat, rectifiedBitmap)

            val cells = ArrayList<Bitmap>(BoardState.SIZE * BoardState.SIZE)
            try {
                val inset = (RECTIFIED_CELL_SIZE * insetFraction).toInt()
                val cropSize = RECTIFIED_CELL_SIZE - 2 * inset
                for (row in 0 until BoardState.SIZE) {
                    for (column in 0 until BoardState.SIZE) {
                        val crop = Bitmap.createBitmap(
                            rectifiedBitmap,
                            column * RECTIFIED_CELL_SIZE + inset,
                            row * RECTIFIED_CELL_SIZE + inset,
                            cropSize,
                            cropSize
                        )
                        val output = try {
                            Bitmap.createScaledBitmap(
                                crop, outputCellSize, outputCellSize, true
                            )
                        } finally {
                            crop.recycle()
                        }
                        cells.add(output)
                    }
                }
                return cells
            } catch (exception: Exception) {
                cells.forEach { if (!it.isRecycled) it.recycle() }
                throw exception
            }
        } finally {
            rectifiedBitmap?.recycle()
            sourceMat.release()
            rectifiedMat.release()
            transform.release()
            sourceCorners.release()
            targetCorners.release()
        }
    }

    companion object {
        const val DEFAULT_OUTPUT_CELL_SIZE = 96
        const val DEFAULT_INSET_FRACTION = 0.12
        private const val RECTIFIED_CELL_SIZE = 96
    }
}
