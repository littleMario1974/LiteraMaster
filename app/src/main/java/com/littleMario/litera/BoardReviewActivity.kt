package com.littleMario.litera

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.DataInputStream
import java.util.concurrent.Executors

/**
 * Ekran wyboru zdjęcia i ręcznej korekty planszy odczytanej przez OCR.
 * Przygotowuje zweryfikowany BoardState dla kolejnego etapu.
 */
class BoardReviewActivity : AppCompatActivity() {

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private lateinit var choosePhotoButton: Button
    private lateinit var analyzeButton: Button
    private lateinit var statusText: TextView
    private lateinit var rackInput: EditText
    private lateinit var rackCountText: TextView
    private lateinit var findMovesButton: Button
    private lateinit var movesText: TextView
    private lateinit var movesActionsContainer: LinearLayout
    private lateinit var previousMoveButton: Button
    private lateinit var nextMoveButton: Button
    private lateinit var previewMoveLabel: TextView
    private lateinit var previewDetailsText: TextView
    private var previewMoves: List<BoardMove> = emptyList()
    private var previewBreakdowns: List<ScoreBreakdown> = emptyList()
    private var selectedMoveIndex: Int? = null
    private var selectedMove: BoardMove? = null
    private lateinit var boardGrid: GridLayout
    private lateinit var photoPreview: ImageView
    private lateinit var colorDiagnosticsText: TextView

    private var sourceBitmap: Bitmap? = null
    private var currentBoard: BoardState = BoardState.empty()
    private var cachedDawgNodes: Array<Node>? = null
    private var unresolvedPositions: Set<Pair<Int, Int>> = emptySet()
    private var suspectedBlankPositions: Set<Pair<Int, Int>> = emptySet()
    private var lowConfidencePositions: Set<Pair<Int, Int>> = emptySet()
    private var recognizedTileColors: Map<Pair<Int, Int>, TileColor?> = emptyMap()
    private var recognizedTileConfidences: Map<Pair<Int, Int>, Float> = emptyMap()
    private var cellViews: Array<Array<TextView?>> =
        Array(BoardState.SIZE) { arrayOfNulls(BoardState.SIZE) }

    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) loadSelectedPhoto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        renderBoard()
    }

    private fun buildScreen() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        content.addView(TextView(this).apply {
            text = "Rozpoznawanie planszy"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        })

        content.addView(TextView(this).apply {
            text = "Wybierz zdjęcie planszy z galerii. Po rozpoznaniu dotknij pola, aby poprawić literę."
            textSize = 14f
            setPadding(0, dp(6), 0, dp(10))
        })

        choosePhotoButton = Button(this).apply {
            text = "Wybierz zdjęcie"
            setOnClickListener { photoPicker.launch("image/*") }
        }
        content.addView(choosePhotoButton)

        analyzeButton = Button(this).apply {
            text = "Rozpoznaj planszę"
            isEnabled = false
            setOnClickListener { analyzePhoto() }
        }
        content.addView(analyzeButton)

        statusText = TextView(this).apply {
            text = "Nie wybrano zdjęcia."
            textSize = 14f
            setPadding(0, dp(8), 0, dp(8))
        }
        content.addView(statusText)

        content.addView(TextView(this).apply {
            text = "Twoje litery na stojaku (maks. 7, ? = blank)"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        })

        rackInput = EditText(this).apply {
            hint = "np. AĄKOTR?"
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            filters = arrayOf(
                android.text.InputFilter.LengthFilter(LetterRack.MAX_TILES)
            )
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int
                ) {
                    updateRackCounter()
                }

                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        content.addView(rackInput)

        rackCountText = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, dp(6))
        }
        content.addView(rackCountText)
        updateRackCounter()

        findMovesButton = Button(this).apply {
            text = "Szukaj możliwych ruchów"
            isEnabled = false
            setOnClickListener { findMoves() }
        }
        content.addView(findMovesButton)

        movesText = TextView(this).apply {
            text = "Wyniki wyszukiwania pojawią się tutaj."
            textSize = 14f
            setPadding(0, dp(4), 0, dp(10))
        }
        content.addView(movesText)

        movesActionsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val previewNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previousMoveButton = Button(this).apply {
            text = "Poprzedni"
            isEnabled = false
            setOnClickListener { selectedMoveIndex?.let { showPreviewAt(it - 1) } }
        }
        previewMoveLabel = TextView(this).apply {
            text = ""
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
        }
        nextMoveButton = Button(this).apply {
            text = "Następny"
            isEnabled = false
            setOnClickListener { selectedMoveIndex?.let { showPreviewAt(it + 1) } }
        }
        previewNavigation.addView(previousMoveButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        previewNavigation.addView(previewMoveLabel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
        previewNavigation.addView(nextMoveButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        movesActionsContainer.addView(previewNavigation)
        previewDetailsText = TextView(this).apply {
            textSize = 14f
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        movesActionsContainer.addView(previewDetailsText)
        movesActionsContainer.addView(Button(this).apply {
            text = "Usuń podgląd ruchu"
            setOnClickListener { clearMovePreview() }
        })
        content.addView(movesActionsContainer)

        photoPreview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            maxHeight = dp(180)
        }
        content.addView(photoPreview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        content.addView(TextView(this).apply {
            text = "Plansza 15 × 15 — dotknij pola, aby je edytować"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(8))
        })

        boardGrid = GridLayout(this).apply {
            columnCount = BoardState.SIZE
            rowCount = BoardState.SIZE
            useDefaultMargins = false
        }
        val boardWidth = resources.displayMetrics.widthPixels - dp(48)
        val cellSize = (boardWidth / BoardState.SIZE).coerceAtLeast(dp(18))

        for (row in 0 until BoardState.SIZE) {
            for (column in 0 until BoardState.SIZE) {
                val cell = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 11f
                    setTextColor(Color.BLACK)
                    setBackgroundColor(Color.rgb(235, 235, 235))
                    setOnClickListener { editCell(row, column) }
                }
                val params = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = cellSize
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                }
                boardGrid.addView(cell, params)
                cellViews[row][column] = cell
            }
        }
        content.addView(boardGrid)

        content.addView(TextView(this).apply {
            text = "Legenda planszy:  * — blank potwierdzony ręcznie   •   ? — możliwy blank (biała płytka)   •   ! — niska pewność OCR (<65%)"
            textSize = 12f
            setPadding(dp(4), dp(6), dp(4), dp(2))
        })

        colorDiagnosticsText = TextView(this).apply {
            text = "Diagnostyka kolorów pojawi się po analizie zdjęcia."
            textSize = 13f
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }
        content.addView(colorDiagnosticsText)

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })
    }

    private fun loadSelectedPhoto(uri: Uri) {
        statusText.text = "Wczytywanie zdjęcia…"
        analyzeButton.isEnabled = false

        analysisExecutor.execute {
            val decoded = try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    null
                } else {
                    val sample = calculateSampleSize(
                        bounds.outWidth, bounds.outHeight, MAX_IMAGE_SIDE
                    )
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                }
            } catch (_: Exception) {
                null
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    decoded?.recycle()
                    return@runOnUiThread
                }
                if (decoded == null) {
                    statusText.text = "Nie udało się wczytać zdjęcia."
                    Toast.makeText(this, "Błąd odczytu zdjęcia", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                sourceBitmap?.recycle()
                sourceBitmap = decoded
                currentBoard = BoardState.empty()
                unresolvedPositions = emptySet()
                suspectedBlankPositions = emptySet()
                lowConfidencePositions = emptySet()
                recognizedTileColors = emptyMap()
                recognizedTileConfidences = emptyMap()
                colorDiagnosticsText.text = "Diagnostyka kolorów pojawi się po analizie zdjęcia."
                renderBoard()
                photoPreview.setImageBitmap(decoded)
                photoPreview.visibility = View.VISIBLE
                analyzeButton.isEnabled = true
                statusText.text = "Zdjęcie gotowe. Uruchom rozpoznawanie."
            }
        }
    }

    private fun analyzePhoto() {
        val bitmap = sourceBitmap ?: return
        analyzeButton.isEnabled = false
        choosePhotoButton.isEnabled = false
        statusText.text = "Wykrywanie planszy i rozpoznawanie 225 pól…"

        analysisExecutor.execute {
            BoardPhotoAnalyzer().analyze(
                source = bitmap,
                onSuccess = { analysis ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        currentBoard = analysis.recognizedBoard
                        unresolvedPositions = analysis.unresolvedCells
                            .map { it.row to it.column }.toSet()
                        val colorDiagnostics = BoardTileColorDiagnostics.from(analysis.cellResults)
                        suspectedBlankPositions = colorDiagnostics.suspectedBlankPositions
                        lowConfidencePositions = BoardOcrConfidenceReview
                            .lowConfidencePositions(analysis.cellResults)
                        recognizedTileColors = analysis.cellResults
                            .filter { it.letter != null }
                            .associate { (it.row to it.column) to it.tileColor }
                        recognizedTileConfidences = analysis.cellResults
                            .filter { it.letter != null && it.confidence != null }
                            .associate { (it.row to it.column) to it.confidence!! }
                        updateColorDiagnostics(colorDiagnostics)
                        renderBoard()
                        val detectedBlanks = analysis.recognizedBoard.occupiedTiles()
                            .count { it.tile.isBlank }
                        statusText.text =
                            "Rozpoznano ${analysis.recognizedTileCount} liter, " +
                            "Potwierdzone blanki: ${detectedBlanks}. " +
                            "Białe płytki do sprawdzenia: ${suspectedBlankPositions.size}. " +
                            "Pola wymagające korekty OCR: ${analysis.unresolvedCount}. " +
                            "Litery o niskiej pewności: ${lowConfidencePositions.size}. " +
                            "Dotknij oznaczonej płytki i zaznacz blank, jeśli to biała płytka."
                        choosePhotoButton.isEnabled = true
                        analyzeButton.isEnabled = true
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        statusText.text = error.message ?: "Nieznany błąd analizy."
                        choosePhotoButton.isEnabled = true
                        analyzeButton.isEnabled = true
                    }
                }
            )
        }
    }

    private fun updateRackCounter() {
        val rack = LetterRack.parse(rackInput.text.toString())
        rackCountText.text = if (rack == null) {
            "Nieprawidłowy stojak — użyj liter polskiego alfabetu i maksymalnie 7 znaków."
        } else {
            "Płytek: ${rack.size}/${LetterRack.MAX_TILES}   Blanki: ${rack.blankCount}"
        }
        rackCountText.setTextColor(
            if (rack == null) Color.RED else Color.DKGRAY
        )
        if (::findMovesButton.isInitialized) {
            findMovesButton.isEnabled = rack != null && rack.size > 0
        }
    }

    private fun findMoves() {
        val rack = LetterRack.parse(rackInput.text.toString())
        if (rack == null || rack.size == 0) {
            Toast.makeText(this, "Wpisz od 1 do 7 płytek.", Toast.LENGTH_SHORT).show()
            return
        }

        val boardSnapshot = currentBoard
        previewMoves = emptyList()
        previewBreakdowns = emptyList()
        selectedMoveIndex = null
        selectedMove = null
        movesActionsContainer.visibility = View.GONE
        previewMoveLabel.text = ""
        renderBoard()
        findMovesButton.isEnabled = false
        movesText.text = "Wyszukiwanie ruchów…"
        statusText.text = "Analizowanie planszy i stojaka…"

        analysisExecutor.execute {
            try {
                val nodes = cachedDawgNodes ?: DataInputStream(
                    assets.open("dictionary.dawg")
                ).use { DawgReader().load(it) }.also { cachedDawgNodes = it }

                val scorer = BoardMoveScorer()
                val rankedMoves = BoardMoveGenerator(nodes).generate(boardSnapshot, rack)
                    .map { move -> move to scorer.explain(boardSnapshot, move) }
                    .sortedWith(
                        compareByDescending<Pair<BoardMove, ScoreBreakdown>> { it.second.total }
                            .thenByDescending { it.first.placements.size }
                            .thenBy { it.first.word }
                    )
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (rankedMoves.isEmpty()) {
                        movesText.text = "Nie znaleziono legalnych ruchów dla podanego stojaka."
                    } else {
                        val shown = rankedMoves.take(MAX_DISPLAYED_MOVES)
                        previewMoves = shown.map { it.first }
                        previewBreakdowns = shown.map { it.second }
                        selectedMoveIndex = null
                        selectedMove = null
                        movesActionsContainer.visibility = View.VISIBLE
                        updatePreviewNavigation()
                        movesText.text = buildString {
                            append("Znalezione propozycje: ${rankedMoves.size}\n")
                            append("Ranking według punktów (premie Literaków uwzględnione).\n\n")
                            shown.forEachIndexed { index, (move, breakdown) ->
                                val direction = if (move.horizontal) "poziomo" else "pionowo"
                                val newTiles = move.placements.joinToString(" ") { placed ->
                                    val mark = if (placed.tile.isBlank) " (blank)" else ""
                                    "${placed.tile.letter}@${placed.row + 1},${placed.column + 1}$mark"
                                }
                                append("${index + 1}. ${move.word.uppercase()} — ${breakdown.total} pkt — $direction, ")
                                append("start ${move.row + 1},${move.column + 1}\n")
                                breakdown.details.forEach { detail ->
                                    append("   $detail\n")
                                }
                                append("   Dokładane: $newTiles\n")
                            }
                            if (rankedMoves.size > shown.size) {
                                append("\nPokazano pierwsze ${shown.size} propozycji.")
                            }
                        }
                    }
                    statusText.text = "Wyszukiwanie zakończone."
                    findMovesButton.isEnabled = true
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    movesText.text = "Nie udało się wyszukać ruchów: ${error.message ?: "błąd słownika"}"
                    statusText.text = "Błąd wyszukiwania."
                    findMovesButton.isEnabled = true
                }
            }
        }
    }

    private fun showPreviewAt(index: Int) {
        if (index !in previewMoves.indices) return
        selectedMoveIndex = index
        selectedMove = previewMoves[index]
        updatePreviewNavigation()
        renderBoard()
        val move = previewMoves[index]
        val breakdown = previewBreakdowns[index]
        val direction = if (move.horizontal) "poziomo" else "pionowo"
        val newTiles = move.placements.joinToString(" ") { placed ->
            val blankMark = if (placed.tile.isBlank) " (blank)" else ""
            "${placed.tile.letter}@${placed.row + 1},${placed.column + 1}${blankMark}"
        }
        previewDetailsText.text = buildString {
            append("${move.word.uppercase()} — ${breakdown.total} pkt\n")
            append("Kierunek: ${direction}; początek: wiersz ${move.row + 1}, kolumna ${move.column + 1}\n")
            append("Dokładane płytki: ${newTiles}\n")
            breakdown.details.forEach { detail -> append("• ${detail}\n") }
        }
        statusText.text = "Podgląd ruchu ${index + 1}/${previewMoves.size}: ${move.word.uppercase()}"
    }

    private fun clearMovePreview() {
        selectedMoveIndex = null
        selectedMove = null
        previewDetailsText.text = "Wybierz ruch, aby zobaczyć jego szczegóły."
        updatePreviewNavigation()
        renderBoard()
        statusText.text = "Podgląd ruchu wyłączony."
    }

    private fun updatePreviewNavigation() {
        val index = selectedMoveIndex
        previewMoveLabel.text = if (index == null) {
            "Wybierz ruch"
        } else {
            "${index + 1}/${previewMoves.size}\n${previewMoves[index].word.uppercase()}"
        }
        previousMoveButton.isEnabled = index != null && index > 0
        nextMoveButton.isEnabled = index != null && index < previewMoves.lastIndex
    }

    private fun renderBoard() {
        for (row in 0 until BoardState.SIZE) {
            for (column in 0 until BoardState.SIZE) {
                val view = cellViews[row][column] ?: continue
                val tile = currentBoard.tileAt(row, column)
                val previewTile = selectedMove?.placements?.firstOrNull {
                    it.row == row && it.column == column
                }?.tile
                val displayedTile = previewTile ?: tile
                val position = row to column
                val suspectedBlank = position in suspectedBlankPositions && tile != null && !tile.isBlank
                view.text = displayedTile?.let { tile ->
                    tile.letter.uppercaseChar().toString() + when {
                        tile.isBlank -> "*"
                        suspectedBlank && previewTile == null -> "?"
                        position in lowConfidencePositions && previewTile == null -> "!"
                        else -> ""
                    }
                } ?: ""
                val unresolved = position in unresolvedPositions
                view.setBackgroundColor(
                    when {
                        previewTile != null -> Color.rgb(255, 170, 70)
                        unresolved -> Color.rgb(255, 224, 130)
                        suspectedBlank -> Color.rgb(220, 210, 255)
                        position in lowConfidencePositions -> Color.rgb(255, 205, 160)
                        tile != null -> Color.rgb(255, 255, 255)
                        else -> Color.rgb(225, 235, 240)
                    }
                )
                view.setTypeface(null, if (displayedTile != null) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    private fun editCell(row: Int, column: Int) {
        clearMovePreview()
        val existingTile = currentBoard.tileAt(row, column)
        val input = EditText(this).apply {
            hint = "Litera przypisana do płytki"
            filters = arrayOf(android.text.InputFilter.LengthFilter(1))
            setSingleLine(true)
            setText(existingTile?.letter?.toString() ?: "")
            setSelection(text.length)
        }
        val blankCheckbox = CheckBox(this).apply {
            text = "To jest blank (biała płytka)"
            isChecked = existingTile?.isBlank == true
        }
        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
            addView(input)
            addView(blankCheckbox)
        }

        val detectedColor = recognizedTileColors[row to column]
        val detectedConfidence = recognizedTileConfidences[row to column]
        val colorDescription = if ((row to column) in recognizedTileColors) {
            "\nKolor odczytany ze zdjęcia: ${colorName(detectedColor)}."
        } else {
            "\nBrak rozpoznanego koloru dla tego pola."
        }
        val confidenceDescription = detectedConfidence?.let {
            "\nPewność OCR: ${"%.0f".format(it * 100)}%."
        } ?: "\nBrak danych o pewności OCR."
        AlertDialog.Builder(this)
            .setTitle("Pole ${row + 1}, ${column + 1}")
            .setMessage("Wpisz literę, którą zastępuje płytka. Blank na planszy również ma przypisaną literę.$colorDescription$confidenceDescription")
            .setView(editor)
            .setNegativeButton("Anuluj", null)
            .setNeutralButton("Wyczyść") { _, _ ->
                currentBoard = currentBoard.withTile(row, column, null)
                unresolvedPositions = unresolvedPositions - (row to column)
                suspectedBlankPositions = suspectedBlankPositions - (row to column)
                lowConfidencePositions = lowConfidencePositions - (row to column)
                recognizedTileConfidences = recognizedTileConfidences - (row to column)
                renderBoard()
            }
            .setPositiveButton("Zapisz") { _, _ ->
                val value = input.text.toString().trim().uppercase()
                if (value.length == 1 && value[0] in POLISH_ALPHABET) {
                    currentBoard = currentBoard.withTile(
                        row, column, BoardTile(value[0], isBlank = blankCheckbox.isChecked)
                    )
                } else {
                    Toast.makeText(
                        this, "Wpisz jedną literę polskiego alfabetu.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                unresolvedPositions = unresolvedPositions - (row to column)
                suspectedBlankPositions = suspectedBlankPositions - (row to column)
                lowConfidencePositions = lowConfidencePositions - (row to column)
                recognizedTileConfidences = recognizedTileConfidences - (row to column)
                renderBoard()
            }
            .show()
    }

    private fun updateColorDiagnostics(diagnostics: BoardTileColorDiagnostics) {
        val counts = diagnostics.colorCounts
        colorDiagnosticsText.text = buildString {
            append("Diagnostyka kolorów rozpoznanych płytek (tylko pola z odczytaną literą):\n")
            append("Żółte: ${counts[TileColor.YELLOW] ?: 0}   ")
            append("Zielone: ${counts[TileColor.GREEN] ?: 0}   ")
            append("Niebieskie: ${counts[TileColor.BLUE] ?: 0}\n")
            append("Czerwone: ${counts[TileColor.RED] ?: 0}   ")
            append("Białe: ${counts[TileColor.WHITE] ?: 0}   ")
            append("Nierozpoznane: ${counts[null] ?: 0}\n")
            if (diagnostics.suspectedBlankPositions.isNotEmpty()) {
                append("Białe pola do sprawdzenia: ")
                append(diagnostics.suspectedBlankPositions
                    .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
                    .joinToString { "${it.first + 1},${it.second + 1}" })
            }
            append("\nKolory są orientacyjne; porównaj je z rzeczywistymi płytkami.")
        }
    }

    private fun colorName(color: TileColor?): String = when (color) {
        TileColor.YELLOW -> "żółty"
        TileColor.GREEN -> "zielony"
        TileColor.BLUE -> "niebieski"
        TileColor.RED -> "czerwony"
        TileColor.WHITE -> "biały (kandydat na blank)"
        null -> "nierozpoznany"
    }

    private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (maxOf(width / (sample * 2), height / (sample * 2)) >= maxSide) {
            sample *= 2
        }
        return sample
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        sourceBitmap?.recycle()
        sourceBitmap = null
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val MAX_IMAGE_SIDE = 2200
        private const val MAX_DISPLAYED_MOVES = 20
        private const val POLISH_ALPHABET =
            "AĄBCĆDEĘFGHIJKLŁMNŃOÓPRSŚTUWYZŹŻ"
    }
}