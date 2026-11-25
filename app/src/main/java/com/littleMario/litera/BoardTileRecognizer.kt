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
        val pixels = mutableListOf<Int>()
        val patchWidth = (bitmap.width * 0.12f).toInt().coerceAtLeast(2)
        val patchHeight = (bitmap.height * 0.12f).toInt().coerceAtLeast(2)
        val left = (bitmap.width * 0.18f).toInt()
        val right = (bitmap.width * 0.70f).toInt()
        val top = (bitmap.height * 0.18f).toInt()
        val bottom = (bitmap.height * 0.70f).toInt()
        for (startY in listOf(top, bottom)) {
            for (startX in listOf(left, right)) {
                for (y in startY until (startY + patchHeight).coerceAtMost(bitmap.height)) {
                    for (x in startX until (startX + patchWidth).coerceAtMost(bitmap.width)) {
                        pixels += bitmap.getPixel(x, y)
                    }
                }
            }
        }
        if (pixels.isEmpty()) return null
        val red = pixels.map { android.graphics.Color.red(it) }.average().toInt()
        val green = pixels.map { android.graphics.Color.green(it) }.average().toInt()
        val blue = pixels.map { android.graphics.Color.blue(it) }.average().toInt()
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
