package com.winlator.cmod.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeBackendProbeTest {
    @Test
    fun `maps identify unixlibs and loaded framegen layers`() {
        val snapshot = RuntimeBackendProbe.inspectMaps(
            listOf(
                "/container/.local/lib/libarm64ecfex.so",
                "/container/.local/lib/libwin_fg.so",
                "/container/.local/lib/liblsfg-vk-layer.so",
            ),
        )

        assertEquals(RuntimeBackendProbe.FexMode.UNIXLIB, snapshot.fexMode)
        assertTrue(snapshot.winFgLoaded)
        assertTrue(snapshot.lsfgLoaded)
    }

    @Test
    fun `dll mode is not reported as unixlibs`() {
        val snapshot = RuntimeBackendProbe.inspectMaps(
            listOf("/container/drive_c/windows/system32/libarm64ecfex.dll"),
        )

        assertEquals(RuntimeBackendProbe.FexMode.DLL, snapshot.fexMode)
        assertFalse(snapshot.winFgLoaded)
        assertFalse(snapshot.lsfgLoaded)
    }
}
