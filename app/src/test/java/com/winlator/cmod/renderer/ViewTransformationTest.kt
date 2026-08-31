package com.winlator.cmod.renderer

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewTransformationTest {
    @Test
    fun appliesFitFillAndIntegerModes() {
        val transform = ViewTransformation()

        transform.update(1920, 1080, 800, 600, ViewTransformation.FULLSCREEN_FIT)
        assertEquals(1440, transform.viewWidth)
        assertEquals(240, transform.viewOffsetX)

        transform.update(1920, 1080, 800, 600, ViewTransformation.FULLSCREEN_FILL)
        assertEquals(1440, transform.viewHeight)
        assertEquals(-180, transform.viewOffsetY)

        transform.update(1920, 1080, 800, 600, ViewTransformation.FULLSCREEN_INTEGER)
        assertEquals(800, transform.viewWidth)
        assertEquals(560, transform.viewOffsetX)
    }
}
