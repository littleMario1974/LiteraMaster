package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardTileColorClassifierTest {

    @Test
    fun recognizesWhiteBlankSurface() {
        assertEquals(TileColor.WHITE, BoardTileColorClassifier.classify(245, 245, 245))
    }

    @Test
    fun recognizesFourColoredTileGroups() {
        assertEquals(TileColor.RED, BoardTileColorClassifier.classify(220, 45, 45))
        assertEquals(TileColor.YELLOW, BoardTileColorClassifier.classify(240, 205, 35))
        assertEquals(TileColor.GREEN, BoardTileColorClassifier.classify(45, 175, 75))
        assertEquals(TileColor.BLUE, BoardTileColorClassifier.classify(45, 95, 210))
    }

    @Test
    fun leavesAmbiguousGrayUnclassified() {
        assertNull(BoardTileColorClassifier.classify(130, 130, 130))
    }
}
