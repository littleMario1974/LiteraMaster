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
    private lateinit var boardGrid: GridLayout
    private lateinit var photoPreview: ImageView

    private var sourceBitmap: Bitmap? = null
    private var currentBoard: BoardState = BoardState.empty()
    private var cachedDawgNodes: Array<Node>? = null
    private var unresolvedPositions: Set<Pair<Int, Int>> = emptySet()
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
                        renderBoard()
                        statusText.text =
                            "Rozpoznano ${analysis.recognizedTileCount} liter. " +
                            "Do sprawdzenia: ${analysis.unresolvedCount} pól."
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
        findMovesButton.isEnabled = false
        movesText.text = "Wyszukiwanie ruchów…"
        statusText.text = "Analizowanie planszy i stojaka…"

        analysisExecutor.execute {
            try {
                val nodes = cachedDawgNodes ?: DawgReader().load(
                    DataInputStream(assets.open("dictionary.dawg"))
                ).also { cachedDawgNodes = it }

                val moves = BoardMoveGenerator(nodes).generate(boardSnapshot, rack)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (moves.isEmpty()) {
                        movesText.text = "Nie znaleziono legalnych ruchów dla podanego stojaka."
                    } else {
                        val shown = moves.take(MAX_DISPLAYED_MOVES)
                        movesText.text = buildString {
                            append("Znalezione propozycje: ${moves.size}\n")
                            append("Kolejność nie uwzględnia jeszcze punktów za pola premiowe.\n\n")
                            shown.forEachIndexed { index, move ->
                                val direction = if (move.horizontal) "poziomo" else "pionowo"
                                val newTiles = move.placements.joinToString(" ") { placed ->
                                    val mark = if (placed.tile.isBlank) " (blank)" else ""
                                    "${placed.tile.letter}@${placed.row + 1},${placed.column + 1}$mark"
                                }
                                append("${index + 1}. ${move.word.uppercase()} — $direction, ")
                                append("start ${move.row + 1},${move.column + 1}\n")
                                append("   Dokładane: $newTiles\n")
                            }
                            if (moves.size > shown.size) {
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

    private fun renderBoard() {
        for (row in 0 until BoardState.SIZE) {
            for (column in 0 until BoardState.SIZE) {
                val view = cellViews[row][column] ?: continue
                val tile = currentBoard.tileAt(row, column)
                view.text = tile?.letter?.uppercaseChar()?.toString() ?: ""
                val unresolved = (row to column) in unresolvedPositions
                view.setBackgroundColor(
                    when {
                        unresolved -> Color.rgb(255, 224, 130)
                        tile != null -> Color.rgb(255, 255, 255)
                        else -> Color.rgb(225, 235, 240)
                    }
                )
                view.setTypeface(null, if (tile != null) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    private fun editCell(row: Int, column: Int) {
        val input = EditText(this).apply {
            hint = "Litera lub puste pole"
            filters = arrayOf(android.text.InputFilter.LengthFilter(1))
            setSingleLine(true)
            setText(currentBoard.tileAt(row, column)?.letter?.toString() ?: "")
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Pole ${row + 1}, ${column + 1}")
            .setMessage("Wpisz jedną literę polskiego alfabetu albo usuń zawartość.")
            .setView(input)
            .setNegativeButton("Anuluj", null)
            .setNeutralButton("Wyczyść") { _, _ ->
                currentBoard = currentBoard.withTile(row, column, null)
                unresolvedPositions = unresolvedPositions - (row to column)
                renderBoard()
            }
            .setPositiveButton("Zapisz") { _, _ ->
                val value = input.text.toString().trim().uppercase()
                if (value.isEmpty()) {
                    currentBoard = currentBoard.withTile(row, column, null)
                } else if (value.length == 1 && value[0] in POLISH_ALPHABET) {
                    currentBoard = currentBoard.withTile(row, column, BoardTile(value[0]))
                } else {
                    Toast.makeText(
                        this, "Wpisz jedną literę polskiego alfabetu.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                unresolvedPositions = unresolvedPositions - (row to column)
                renderBoard()
            }
            .show()
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
