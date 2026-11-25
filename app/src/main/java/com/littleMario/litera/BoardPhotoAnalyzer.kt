package com.littleMario.litera

import android.graphics.Bitmap

/**
 * Łączy wykrycie planszy, wycięcie 225 pól i rozpoznawanie liter.
 *
 * Bitmapa źródłowa pozostaje własnością wywołującego i nie jest zwalniana.
 * Bitmapy wyciętych pól są zwalniane po zakończeniu OCR albo po błędzie.
 */
class BoardPhotoAnalyzer(
    private val detector: BoardImageDetector = BoardImageDetector(),
    private val extractor: BoardCellImageExtractor = BoardCellImageExtractor(),
    private val recognizerFactory: () -> BoardTileRecognizer = { BoardTileRecognizer() }
) {

    fun analyze(
        source: Bitmap,
        onSuccess: (BoardPhotoAnalysis) -> Unit,
        onError: (BoardPhotoAnalysisException) -> Unit
    ) {
        if (source.isRecycled) {
            onError(BoardPhotoAnalysisException.SourceImageUnavailable())
            return
        }

        val geometry = try {
            detector.detect(source)
        } catch (exception: Exception) {
            onError(BoardPhotoAnalysisException.BoardDetectionFailed(exception))
            return
        }

        if (geometry == null) {
            onError(BoardPhotoAnalysisException.BoardNotFound())
            return
        }

        val cellBitmaps = try {
            extractor.extractCells(source, geometry)
        } catch (exception: Exception) {
            onError(BoardPhotoAnalysisException.CellExtractionFailed(exception))
            return
        }

        val recognizer = try {
            recognizerFactory()
        } catch (exception: Exception) {
            recycleCells(cellBitmaps)
            onError(BoardPhotoAnalysisException.OcrFailed(exception))
            return
        }

        var finished = false

        fun finishWithError(exception: Exception) {
            if (finished) return
            finished = true
            recycleCells(cellBitmaps)
            recognizer.close()
            onError(BoardPhotoAnalysisException.OcrFailed(exception))
        }

        try {
            recognizer.recognizeBoard(
                cells = cellBitmaps,
                onComplete = { results ->
                    if (finished) return@recognizeBoard
                    finished = true
                    recycleCells(cellBitmaps)
                    recognizer.close()

                    try {
                        onSuccess(BoardPhotoAnalysisMapper.map(geometry, results))
                    } catch (exception: Exception) {
                        onError(BoardPhotoAnalysisException.InvalidOcrResults(exception))
                    }
                },
                onError = ::finishWithError
            )
        } catch (exception: Exception) {
            finishWithError(exception)
        }
    }

    private fun recycleCells(cells: List<Bitmap>) {
        cells.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}

/**
 * Błędy analizy rozróżnione tak, aby interfejs mógł pokazać
 * użytkownikowi konkretną przyczynę niepowodzenia.
 */
sealed class BoardPhotoAnalysisException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    class SourceImageUnavailable :
        BoardPhotoAnalysisException("Obraz źródłowy jest niedostępny.")

    class BoardNotFound :
        BoardPhotoAnalysisException("Nie udało się znaleźć planszy na zdjęciu.")

    class BoardDetectionFailed(cause: Throwable) :
        BoardPhotoAnalysisException("Wystąpił błąd podczas wykrywania planszy.", cause)

    class CellExtractionFailed(cause: Throwable) :
        BoardPhotoAnalysisException("Nie udało się wyciąć pól planszy.", cause)

    class OcrFailed(cause: Throwable) :
        BoardPhotoAnalysisException("Rozpoznawanie liter nie powiodło się.", cause)

    class InvalidOcrResults(cause: Throwable) :
        BoardPhotoAnalysisException("Wyniki rozpoznawania mają nieprawidłowy format.", cause)
}
