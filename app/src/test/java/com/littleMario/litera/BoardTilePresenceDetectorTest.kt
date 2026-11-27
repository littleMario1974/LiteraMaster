package com.littleMario.litera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTilePresenceDetectorTest {

    @Test
    fun acceptsLargeColoredTileCoveringAlmostWholeCell() {
        assertTrue(
            BoardTilePresenceDetector.isLikelyTileMetrics(
                width = 96,
                height = 96,
                minX = 2,
                minY = 3,
                maxX = 93,
                maxY = 92,
                coloredPixels = 4_800
            )
        )
    }

    @Test
    fun rejectsSmallPremiumSquareEvenWhenItsColorIsStrong() {
        assertFalse(
            BoardTilePresenceDetector.isLikelyTileMetrics(
                width = 96,
                height = 96,
                minX = 10,
                minY = 11,
                maxX = 85,
                maxY = 84,
                coloredPixels = 3_900
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
