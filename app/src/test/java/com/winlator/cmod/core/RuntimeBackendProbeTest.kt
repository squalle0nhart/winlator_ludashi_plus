package com.winlator.cmod.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeBackendProbeTest {
    @Test
    fun `maps distinguish unixlibs from dll mode`() {
        assertEquals(
            RuntimeBackendProbe.FexMode.UNIXLIB,
            RuntimeBackendProbe.inspectMaps(listOf("/container/.local/lib/libarm64ecfex.so")),
        )
        assertEquals(
            RuntimeBackendProbe.FexMode.DLL,
            RuntimeBackendProbe.inspectMaps(listOf("/container/system32/libarm64ecfex.dll")),
        )
    }
}
