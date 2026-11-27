package com.littleMario.litera

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class BoardCellOcrResult(
    val row: Int,
    val column: Int,
    val letter: Char?,
    val rawText: String,
    val confidence: Float?,
    val tileColor: TileColor? = null
)

/**
 * OCR uruchamiany kolejno dla 225 pól.
 * Każde pole jest najpierw przetwarzane w wersji szarości z poprawionym
 * kontrastem, a gdy nie ma litery — ponownie w wersji adaptacyjnie binarnej.
 * Kolor płytki zawsze odczytywany jest z oryginalnego obrazu pola.
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

            val original = cells[index]
            val row = index / BoardState.SIZE
            val column = index % BoardState.SIZE
            val variants = try {
                BoardOcrImagePreprocessor.createVariants(original)
            } catch (exception: Exception) {
                onError(exception)
                return
            }

            var bestResult: BoardCellOcrResult? = null

            fun recycleVariants() {
                variants.forEach { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }

            fun recognizeVariant(variantIndex: Int) {
                if (variantIndex >= variants.size) {
                    results.add(bestResult ?: BoardCellOcrResult(
                        row = row,
                        column = column,
                        letter = null,
                        rawText = "",
                        confidence = null,
                        tileColor = recognizeTileColorFromCorners(original)
                    ))
                    recycleVariants()
                    recognizeAt(index + 1)
                    return
                }

                val input = try {
                    InputImage.fromBitmap(variants[variantIndex], 0)
                } catch (exception: Exception) {
                    recycleVariants()
                    onError(exception)
                    return
                }

                try {
                    recognizer.process(input)
                        .addOnSuccessListener { text ->
                            try {
                                val candidate = toCellResult(row, column, text, original)
                                val current = bestResult
                                if (current == null ||
                                    (candidate.letter != null && current.letter == null) ||
                                    ((candidate.confidence ?: 0f) > (current.confidence ?: 0f))
                                ) {
                                    bestResult = candidate
                                }

                                // Jeśli kontrastowa szarość dała literę,
                                // nie ma potrzeby ryzykować utraty szczegółów binaryzacją.
                                if (candidate.letter != null) {
                                    results.add(candidate)
                                    recycleVariants()
                                    recognizeAt(index + 1)
                                } else {
                                    recognizeVariant(variantIndex + 1)
                                }
                            } catch (exception: Exception) {
                                recycleVariants()
                                onError(exception)
                            }
                        }
                        .addOnFailureListener { exception ->
                            recycleVariants()
                            onError(exception)
                        }
                } catch (exception: Exception) {
                    recycleVariants()
                    onError(exception)
                }
            }

            recognizeVariant(0)
        }

        recognizeAt(0)
    }

    fun close() {
        recognizer.close()
    }

    private fun recognizeTileColorFromCorners(bitmap: Bitmap): TileColor? {
        if (bitmap.width < 8 || bitmap.height < 8) return null
        val patchWidth = (bitmap.width * 0.12f).toInt().coerceAtLeast(2)
        val patchHeight = (bitmap.height * 0.12f).toInt().coerceAtLeast(2)
        val left = (bitmap.width * 0.18f).toInt()
        val right = (bitmap.width * 0.70f).toInt()
        val top = (bitmap.height * 0.18f).toInt()
        val bottom = (bitmap.height * 0.70f).toInt()

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
        return BoardTileColorClassifier.classify(
            (redSum / pixelCount).toInt(),
            (greenSum / pixelCount).toInt(),
            (blueSum / pixelCount).toInt()
        )
    }

    private fun toCellResult(
        row: Int,
        column: Int,
        text: Text,
        originalBitmap: Bitmap
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
            tileColor = recognizeTileColorFromCorners(originalBitmap)
        )
    }
}
