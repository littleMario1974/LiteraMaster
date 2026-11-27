package com.littleMario.litera

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
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
 * Wykrywa zewnętrzny obrys planszy 15 x 15.
 * Próbuje kilku progów Canny i toleruje większą perspektywę zdjęcia.
 */
class BoardImageDetector(
    private val minimumBoardAreaRatio: Double = 0.04,
    private val minimumAspectRatio: Double = 0.45,
    private val maximumAspectRatio: Double = 2.20,
    private val maximumProcessingSide: Int = 1800
) {
    init {
        require(minimumBoardAreaRatio in 0.0..1.0)
        require(minimumAspectRatio > 0.0)
        require(maximumAspectRatio >= minimumAspectRatio)
        require(maximumProcessingSide >= 300)
    }

    fun detect(source: Bitmap): BoardGridGeometry? {
        require(!source.isRecycled) { "Obraz źródłowy został zwolniony." }
        check(OpenCVLoader.initLocal()) {
            "Nie udało się uruchomić biblioteki OpenCV."
        }

        val scale = min(1.0, maximumProcessingSide.toDouble() / max(source.width, source.height))
        val width = max(1, (source.width * scale).toInt())
        val height = max(1, (source.height * scale).toInt())
        val input = if (width != source.width || height != source.height) {
            Bitmap.createScaledBitmap(source, width, height, true)
        } else source

        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        try {
            Utils.bitmapToMat(input, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            val imageArea = width.toDouble() * height
            var best: Candidate? = null
            // Kilka progów zwiększa szansę wykrycia zarówno słabego,
            // jak i mocnego kontrastu krawędzi planszy.
            val thresholds = listOf(30.0 to 100.0, 50.0 to 150.0, 75.0 to 200.0)
            val approximations = listOf(0.015, 0.02, 0.03)

            for ((low, high) in thresholds) {
                Imgproc.Canny(blurred, edges, low, high)
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
                try {
                    Imgproc.dilate(edges, edges, kernel)
                } finally {
                    kernel.release()
                }

                val contours = mutableListOf<MatOfPoint>()
                try {
                    Imgproc.findContours(
                        edges, contours, hierarchy,
                        Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
                    )
                    for (contour in contours) {
                        val area = abs(Imgproc.contourArea(contour))
                        if (area < imageArea * minimumBoardAreaRatio) continue

                        val curve = MatOfPoint2f(*contour.toArray())
                        try {
                            val perimeter = Imgproc.arcLength(curve, true)
                            for (epsilon in approximations) {
                                val polygon = MatOfPoint2f()
                                try {
                                    Imgproc.approxPolyDP(curve, polygon, perimeter * epsilon, true)
                                    val points = polygon.toArray()
                                    if (points.size != 4) continue
                                    val convexPolygon = MatOfPoint(*points)
                                    val convex = try {
                                        Imgproc.isContourConvex(convexPolygon)
                                    } finally {
                                        convexPolygon.release()
                                    }
                                    if (!convex) continue

                                    val ordered = orderCorners(points) ?: continue
                                    val w = (distance(ordered[0], ordered[1]) +
                                        distance(ordered[3], ordered[2])) / 2.0
                                    val h = (distance(ordered[0], ordered[3]) +
                                        distance(ordered[1], ordered[2])) / 2.0
                                    if (w <= 0.0 || h <= 0.0) continue
                                    val aspect = w / h
                                    if (aspect !in minimumAspectRatio..maximumAspectRatio) continue

                                    val areaRatio = area / imageArea
                                    val shapePenalty = abs(kotlin.math.ln(aspect))
                                    val score = areaRatio / (1.0 + shapePenalty)
                                    if (best == null || score > best.score) {
                                        best = Candidate(ordered, score)
                                    }
                                } finally {
                                    polygon.release()
                                }
                            }
                        } finally {
                            curve.release()
                        }
                    }
                } finally {
                    contours.forEach { it.release() }
                }
            }

            val candidate = best ?: return null
            val factorX = source.width.toDouble() / width
            val factorY = source.height.toDouble() / height
            val p = candidate.points
            return BoardGridGeometry(
                topLeft = ImagePoint(p[0].x * factorX, p[0].y * factorY),
                topRight = ImagePoint(p[1].x * factorX, p[1].y * factorY),
                bottomRight = ImagePoint(p[2].x * factorX, p[2].y * factorY),
                bottomLeft = ImagePoint(p[3].x * factorX, p[3].y * factorY)
            )
        } finally {
            rgba.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
            if (input !== source) input.recycle()
        }
    }

    private fun orderCorners(points: Array<Point>): Array<Point>? {
        if (points.size != 4) return null
        val tl = points.minByOrNull { it.x + it.y } ?: return null
        val br = points.maxByOrNull { it.x + it.y } ?: return null
        val tr = points.minByOrNull { it.y - it.x } ?: return null
        val bl = points.maxByOrNull { it.y - it.x } ?: return null
        val ordered = arrayOf(tl, tr, br, bl)
        return ordered.takeIf { it.toSet().size == 4 }
    }

    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private data class Candidate(val points: Array<Point>, val score: Double)
}