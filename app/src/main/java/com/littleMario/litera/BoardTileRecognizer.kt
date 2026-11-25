package com.littleMario.litera

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
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
    val confidence: Float?
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

            recognizer.process(image)
                .addOnSuccessListener { text ->
                    results.add(toCellResult(row, column, text))
                    recognizeAt(index + 1)
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

    private fun toCellResult(
        row: Int,
        column: Int,
        text: Text
    ): BoardCellOcrResult {
        val confidences = text.textBlocks
            .flatMap { block -> block.lines }
            .flatMap { line -> line.elements }
            .mapNotNull { element -> element.confidence }

        val confidence = confidences.takeIf { it.isNotEmpty() }?.average()?.toFloat()

        return BoardCellOcrResult(
            row = row,
            column = column,
            letter = BoardTileTextParser.parse(text.text),
            rawText = text.text,
            confidence = confidence
        )
    }
}
