package com.littleMario.litera

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Wynik OCR dla jednego pola planszy.
 *
 * letter == null oznacza, że pole jest puste albo OCR nie rozpoznał
 * jednoznacznie jednej litery. rawText pozwala później pokazać wynik
 * do ręcznej korekty zamiast zgadywać.
 */
data class BoardCellOcrResult(
    val row: Int,
    val column: Int,
    val letter: Char?,
    val rawText: String,
    val confidence: Float?,
    val tileColor: TileColor? = null
)

/**
 * Rozpoznaje tekst na wyciętych bitmapach pól.
 *
 * Rozpoznawanie odbywa się kolejno, a nie równolegle, aby nie uruchamiać
 * 225 zadań ML Kit naraz. Bitmapy pozostają własnością wywołującego.
 */
class BoardTileRecognizer {

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    fun recognizeBoard(
        cells: List<Bitmap>,
        onComplete: (List<BoardCellOcrResult>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        require(cells.size == BoardState.SIZE * BoardState.SIZE) {
            "OCR wymaga dokładnie 225 obrazów pól."
        }

        val results = ArrayList<BoardCellOcrResult>(cells.size)

        fun recognizeAt(index: Int) {
            if (index >= cells.size) {
                onComplete(results)
                return
            }

            val row = index / BoardState.SIZE
            val column = index % BoardState.SIZE
            val image = try {
                InputImage.fromBitmap(cells[index], 0)
            } catch (exception: Exception) {
                onError(exception)
                return
            }

            val recognitionTask = try {
                recognizer.process(image)
            } catch (exception: Exception) {
                onError(exception)
                return
            }

            recognitionTask
                .addOnSuccessListener { text ->
                    try {
                        results.add(toCellResult(row, column, text, cells[index]))
                        recognizeAt(index + 1)
                    } catch (exception: Exception) {
                        // Exceptions raised in asynchronous listeners are not
                        // caught by BoardPhotoAnalyzer's original call stack.
                        // Forward them so the analyzer can release cell bitmaps.
                        onError(exception)
                    }
                }
                .addOnFailureListener { exception ->
                    onError(exception)
                }
        }

        recognizeAt(0)
    }

    fun close() {
        recognizer.close()
    }

    /**
     * Próbkuje cztery narożne fragmenty pola, z dala od nadrukowanej litery.
     * Kolor jest wskazówką, nie zastępuje ręcznej kontroli zdjęcia.
     */
    private fun recognizeTileColorFromCorners(bitmap: Bitmap): TileColor? {
        if (bitmap.width < 8 || bitmap.height < 8) return null
        val patchWidth = (bitmap.width * 0.12f).toInt().coerceAtLeast(2)
        val patchHeight = (bitmap.height * 0.12f).toInt().coerceAtLeast(2)
        val left = (bitmap.width * 0.18f).toInt()
        val right = (bitmap.width * 0.70f).toInt()
        val top = (bitmap.height * 0.18f).toInt()
        val bottom = (bitmap.height * 0.70f).toInt()

        // Accumulate channels directly instead of allocating a boxed pixel list
        // and three intermediate lists for every one of the 225 board cells.
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var pixelCount = 0L

        for (vertical in 0..1) {
            val startY = if (vertical == 0) top else bottom
            for (horizontal in 0..1) {
                val startX = if (horizontal == 0) left else right
                val endY = (startY + patchHeight).coerceAtMost(bitmap.height)
                val endX = (startX + patchWidth).coerceAtMost(bitmap.width)

                for (y in startY until endY) {
                    for (x in startX until endX) {
                        val pixel = bitmap.getPixel(x, y)
                        redSum += android.graphics.Color.red(pixel)
                        greenSum += android.graphics.Color.green(pixel)
                        blueSum += android.graphics.Color.blue(pixel)
                        pixelCount++
                    }
                }
            }
        }

        if (pixelCount == 0L) return null
        val red = (redSum / pixelCount).toInt()
        val green = (greenSum / pixelCount).toInt()
        val blue = (blueSum / pixelCount).toInt()
        return BoardTileColorClassifier.classify(red, green, blue)
    }

    private fun toCellResult(
        row: Int,
        column: Int,
        text: Text,
        bitmap: Bitmap
    ): BoardCellOcrResult {
        val confidence = BoardOcrConfidence.calculate(
            text.textBlocks
                .flatMap { block -> block.lines }
                .flatMap { line -> line.elements }
                .map { element -> element.text to element.confidence }
        )

        return BoardCellOcrResult(
            row = row,
            column = column,
            letter = BoardTileTextParser.parse(text.text),
            rawText = text.text,
            confidence = confidence,
            tileColor = recognizeTileColorFromCorners(bitmap)
        )
    }
}
