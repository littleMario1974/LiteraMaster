package com.littleMario.litera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGlyphFilterTest {

    @Test
    fun acceptsLetterSizedGlyph() {
        assertTrue(BoardGlyphFilter.isTileSized(glyphHeight = 42, cellHeight = 96))
    }

    @Test
    fun rejectsSmallPremiumMarking() {
        assertFalse(BoardGlyphFilter.isTileSized(glyphHeight = 20, cellHeight = 96))
    }

    @Test
    fun rejectsMissingOrInvalidBounds() {
        assertFalse(BoardGlyphFilter.isTileSized(glyphHeight = 0, cellHeight = 96))
        assertFalse(BoardGlyphFilter.isTileSized(glyphHeight = 30, cellHeight = 0))
    }
}
