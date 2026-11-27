package com.littleMario.litera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTilePresenceDetectorTest {

    @Test
    fun acceptsTileAfterCellInset() {
        // Tile covering roughly 72% of the cropped cell, with some
        // area lost to the printed letter, glare and perspective.
        assertTrue(
            BoardTilePresenceDetector.isLikelyTileMetrics(
                width = 96,
                height = 96,
                minX = 13,
                minY = 14,
                maxX = 82,
                maxY = 81,
                coloredPixels = 2_500
            )
        )
    }

    @Test
    fun rejectsSmallerPrintedPremiumMarking() {
        // A printed premium square is substantially smaller than a tile.
        assertFalse(
            BoardTilePresenceDetector.isLikelyTileMetrics(
                width = 96,
                height = 96,
                minX = 25,
                minY = 26,
                maxX = 70,
                maxY = 69,
                coloredPixels = 1_200
            )
        )
    }

    @Test
    fun rejectsSparseGridAndLetterMarkings() {
        assertFalse(
            BoardTilePresenceDetector.isLikelyTileMetrics(
                width = 96,
                height = 96,
                minX = 8,
                minY = 9,
                maxX = 88,
                maxY = 87,
                coloredPixels = 1_500
            )
        )
    }
}
