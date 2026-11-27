package com.littleMario.litera

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Przygotowuje dwie wersje pojedynczego pola do OCR.
 * Wersja 1: skala szarości z wyrównanym histogramem.
 * Wersja 2: adaptacyjna binaryzacja, używana jako ponowna próba OCR.
 *
 * Oryginalna bitmapa nie jest modyfikowana — pozostaje źródłem koloru płytki.
 */
object BoardOcrImagePreprocessor {

    fun createVariants(source: Bitmap): List<Bitmap> {
        require(!source.isRecycled) { "Obraz pola został zwolniony." }
        check(OpenCVLoader.initLocal()) {
            "Nie udało się uruchomić OpenCV podczas przygotowania obrazu OCR."
        }

        val rgba = Mat()
        val gray = Mat()
        val enhanced = Mat()
        val binary = Mat()
        val variants = ArrayList<Bitmap>(2)

        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            // Wyrównanie histogramu poprawia czytelność liter przy nierównym
            // oświetleniu i niskim kontraście, nie ingerując w oryginalne kolory.
            Imgproc.equalizeHist(gray, enhanced)

            val enhancedBitmap = Bitmap.createBitmap(
                source.width, source.height, Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(enhanced, enhancedBitmap)
            variants.add(enhancedBitmap)

            // Lokalny próg adaptacyjny lepiej radzi sobie z cieniami niż
            // jeden próg globalny. To wariant zapasowy, nie zamiennik kolorowego źródła.
            val blockSize = (minOf(source.width, source.height) / 3)
                .coerceAtLeast(3).let { if (it % 2 == 0) it + 1 else it }
            if (blockSize < minOf(source.width, source.height)) {
                Imgproc.adaptiveThreshold(
                    enhanced,
                    binary,
                    255.0,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY,
                    blockSize,
                    7.0
                )
                val binaryBitmap = Bitmap.createBitmap(
                    source.width, source.height, Bitmap.Config.ARGB_8888
                )
                Utils.matToBitmap(binary, binaryBitmap)
                variants.add(binaryBitmap)
            }
            return variants
        } catch (exception: Exception) {
            variants.forEach { if (!it.isRecycled) it.recycle() }
            throw exception
        } finally {
            rgba.release()
            gray.release()
            enhanced.release()
            binary.release()
        }
    }
}
