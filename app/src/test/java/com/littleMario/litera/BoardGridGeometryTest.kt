package com.littleMario.litera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardGridGeometryTest {

    @Test
    fun mapsCornersToImageCorners() {
        val geometry = rectangleGeometry()

        assertEquals(ImagePoint(100.0, 50.0), geometry.pointAt(0.0, 0.0))
        assertEquals(ImagePoint(700.0, 50.0), geometry.pointAt(1.0, 0.0))
        assertEquals(ImagePoint(700.0, 650.0), geometry.pointAt(1.0, 1.0))
        assertEquals(ImagePoint(100.0, 650.0), geometry.pointAt(0.0, 1.0))
    }

    @Test
    fun calculatesCenterOfMiddleCell() {
        val geometry = rectangleGeometry()

        val center = geometry.cellCenter(7, 7)

        assertEquals(400.0, center.x, 0.000001)
        assertEquals(350.0, center.y, 0.000001)
    }

    @Test
    fun supportsPerspectiveQuadrilateral() {
        val geometry = BoardGridGeometry(
            topLeft = ImagePoint(100.0, 80.0),
            topRight = ImagePoint(700.0, 120.0),
            bottomRight = ImagePoint(760.0, 680.0),
            bottomLeft = ImagePoint(60.0, 620.0)
        )

        val center = geometry.pointAt(0.5, 0.5)

        assertTrue(center.x in 400.0..410.0)
        assertTrue(center.y in 370.0..390.0)
    }

    @Test
    fun cellHasFourCorners() {
        val geometry = rectangleGeometry()

        assertEquals(4, geometry.cellCorners(0, 0).size)
        assertEquals(4, geometry.cellCorners(14, 14).size)
    }

    private fun rectangleGeometry() = BoardGridGeometry(
        topLeft = ImagePoint(100.0, 50.0),
        topRight = ImagePoint(700.0, 50.0),
        bottomRight = ImagePoint(700.0, 650.0),
        bottomLeft = ImagePoint(100.0, 650.0)
    )
}
