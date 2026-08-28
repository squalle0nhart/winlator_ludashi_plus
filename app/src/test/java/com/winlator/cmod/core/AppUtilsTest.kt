package com.winlator.cmod.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUtilsTest {
    @Test
    fun screenSizesPutExactThenMatchingAspectRatioAndCustomLast() {
        val entries = arrayOf(
            "Custom",
            "640x360 (16:9)",
            "780x360 (19.5:9)",
            "1280x720 (16:9)",
            "1920x1080 (16:9)",
        )

        assertEquals(
            listOf(
                "1920x1080 (16:9)",
                "640x360 (16:9)",
                "1280x720 (16:9)",
                "780x360 (19.5:9)",
                "Custom",
            ),
            AppUtils.orderScreenSizeEntries(entries, "1920x1080"),
        )

        assertEquals(
            listOf(
                "2400x1080 (20:9)",
                "1280x576 (20:9)",
                "640x360 (16:9)",
                "Custom",
            ),
            AppUtils.orderScreenSizeEntries(
                arrayOf("Custom", "640x360 (16:9)", "1280x576 (20:9)"),
                "2400x1080",
            ),
        )
    }
}
