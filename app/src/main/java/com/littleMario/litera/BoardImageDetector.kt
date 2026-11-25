package com.littleMario.litera

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Wykrywa zewnętrzny obrys kwadratowej planszy 15 x 15 na fotografii.
 *
 * Pierwsza wersja wykorzystuje kontury i aproksymację wielokątów OpenCV.
 * Zwraca null, jeśli nie uda się znaleźć wiarygodnego czworokąta.
 *
 * To etap lokalizacji planszy, a nie rozpoznawania liter na polach.
 */
class BoardImageDetector(
    private val minimumBoardAreaRatio: Double = 0.08,
    private val minimumAspectRatio: Double = 0.65,
    private val maximumAspectRatio: Double = 1.50,
    private val maximumProcessingSide: Int = 1600
) {

    init {
        require(minimumBoardAreaRatio in 0.0..1.0)
        require(minimumAspectRatio > 0.0)
        require(maximumAspectRatio >= minimumAspectRatio)
        require(maximumProcessingSide >= 300)
    }

    /**
     * @return geometria planszy w pikselach oryginalnego Bitmap albo null.
     */
    fun detect(source: Bitmap): BoardGridGeometry? {
        require(!source.isRecycled) { "Obraz źródłowy został zwolniony." }
        if (!OpenCVLoader.initLocal()) return null

        val scale = min(
            1.0,
            maximumProcessingSide.toDouble() / max(source.width, source.height)
        )
        val processingWidth = max(1, (source.width * scale).toInt())
        val processingHeight = max(1, (source.height * scale).toInt())

        val inputBitmap = if (processingWidth != source.width ||
            processingHeight != source.height
        ) {
            Bitmap.createScaledBitmap(source, processingWidth, processingHeight, true)
        } else {
            source
        }

        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()

        try {
            Utils.bitmapToMat(inputBitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)
            Imgproc.dilate(
                edges,
                edges,
                Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            )

            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val imageArea = processingWidth.toDouble() * processingHeight
            var best: Candidate? = null

            for (contour in contours) {
                val contourArea = abs(Imgproc.contourArea(contour))
                if (contourArea < imageArea * minimumBoardAreaRatio) continue

                val curve = MatOfPoint2f(*contour.toArray())
                val polygon = MatOfPoint2f()
                try {
                    val perimeter = Imgproc.arcLength(curve, true)
                    Imgproc.approxPolyDP(curve, polygon, perimeter * 0.02, true)

                    val points = polygon.toArray()
                    if (points.size != 4) continue

                    val polygonForConvexity = MatOfPoint(*points)
                    val convex = try {
                        Imgproc.isContourConvex(polygonForConvexity)
                    } finally {
                        polygonForConvexity.release()
                    }
                    if (!convex) continue

                    val ordered = orderCorners(points) ?: continue
                    val width = (distance(ordered[0], ordered[1]) +
                        distance(ordered[3], ordered[2])) / 2.0
                    val height = (distance(ordered[0], ordered[3]) +
                        distance(ordered[1], ordered[2])) / 2.0
                    if (width <= 0.0 || height <= 0.0) continue

                    val aspect = width / height
                    if (aspect !in minimumAspectRatio..maximumAspectRatio) continue

                    val areaRatio = contourArea / imageArea
                    val squarePenalty = abs(kotlin.math.ln(aspect))
                    val score = areaRatio / (1.0 + squarePenalty)
                    if (best == null || score > best.score) {
                        best = Candidate(ordered, score)
                    }
                } finally {
                    curve.release()
                    polygon.release()
                }
            }

            val candidate = best ?: return null
            val factorX = source.width.toDouble() / processingWidth
            val factorY = source.height.toDouble() / processingHeight
            val p = candidate.points

            return BoardGridGeometry(
                topLeft = ImagePoint(p[0].x * factorX, p[0].y * factorY),
                topRight = ImagePoint(p[1].x * factorX, p[1].y * factorY),
                bottomRight = ImagePoint(p[2].x * factorX, p[2].y * factorY),
                bottomLeft = ImagePoint(p[3].x * factorX, p[3].y * factorY)
            )
        } finally {
            contours.forEach { it.release() }
            rgba.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
            if (inputBitmap !== source) inputBitmap.recycle()
        }
    }

    private fun orderCorners(points: Array<Point>): Array<Point>? {
        if (points.size != 4) return null

        val topLeft = points.minByOrNull { it.x + it.y } ?: return null
        val bottomRight = points.maxByOrNull { it.x + it.y } ?: return null
        val topRight = points.minByOrNull { it.y - it.x } ?: return null
        val bottomLeft = points.maxByOrNull { it.y - it.x } ?: return null

        val ordered = arrayOf(topLeft, topRight, bottomRight, bottomLeft)
        if (ordered.toSet().size != 4) return null
        return ordered
    }

    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private data class Candidate(
        val points: Array<Point>,
        val score: Double
    )
}
