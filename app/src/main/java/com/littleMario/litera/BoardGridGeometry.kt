package com.littleMario.litera

/**
 * Geometria planszy wyznaczona na obrazie.
 *
 * Punkty narożne podaje się w układzie współrzędnych obrazu:
 * (0, 0) = lewy górny piksel, x rośnie w prawo, y w dół.
 *
 * Kolejność narożników jest istotna: lewy-górny, prawy-górny,
 * prawy-dolny, lewy-dolny. Dzięki temu można opisać również
 * planszę sfotografowaną pod kątem.
 */
data class ImagePoint(
    val x: Double,
    val y: Double
)

data class BoardGridGeometry(
    val topLeft: ImagePoint,
    val topRight: ImagePoint,
    val bottomRight: ImagePoint,
    val bottomLeft: ImagePoint
) {
    /**
     * Zwraca środek pola planszy.
     * row i column należą do zakresu 0..14.
     */
    fun cellCenter(row: Int, column: Int): ImagePoint {
        require(row in 0 until BoardState.SIZE) { "Nieprawidłowy wiersz: $row" }
        require(column in 0 until BoardState.SIZE) { "Nieprawidłowa kolumna: $column" }

        val u = (column + 0.5) / BoardState.SIZE
        val v = (row + 0.5) / BoardState.SIZE
        return pointAt(u, v)
    }

    /**
     * Zwraca punkt w obrębie planszy.
     * u = 0 oznacza lewą krawędź, u = 1 prawą.
     * v = 0 oznacza górną krawędź, v = 1 dolną.
     *
     * Interpolacja biliniowa uwzględnia perspektywiczne
     * zbieganie się krawędzi na zdjęciu.
     */
    fun pointAt(u: Double, v: Double): ImagePoint {
        require(u in 0.0..1.0) { "u musi należeć do zakresu 0..1" }
        require(v in 0.0..1.0) { "v musi należeć do zakresu 0..1" }

        val top = interpolate(topLeft, topRight, u)
        val bottom = interpolate(bottomLeft, bottomRight, u)
        return interpolate(top, bottom, v)
    }

    /**
     * Przybliżone granice pola jako cztery punkty:
     * lewy-górny, prawy-górny, prawy-dolny, lewy-dolny.
     */
    fun cellCorners(row: Int, column: Int): List<ImagePoint> {
        require(row in 0 until BoardState.SIZE) { "Nieprawidłowy wiersz: $row" }
        require(column in 0 until BoardState.SIZE) { "Nieprawidłowa kolumna: $column" }

        val left = column.toDouble() / BoardState.SIZE
        val right = (column + 1.0) / BoardState.SIZE
        val top = row.toDouble() / BoardState.SIZE
        val bottom = (row + 1.0) / BoardState.SIZE

        return listOf(
            pointAt(left, top),
            pointAt(right, top),
            pointAt(right, bottom),
            pointAt(left, bottom)
        )
    }

    private fun interpolate(a: ImagePoint, b: ImagePoint, fraction: Double) =
        ImagePoint(
            x = a.x + (b.x - a.x) * fraction,
            y = a.y + (b.y - a.y) * fraction
        )
}
