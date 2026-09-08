package com.winlator.cmod.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameGenManagerTest {
    @Test
    fun legacyBackendAndLsfgPresentModeMigrateCleanly() {
        assertEquals(FrameGenManager.BACKEND_LSFG_NATIVE, FrameGenManager.normalizeBackend("lsfg_native"))
        assertEquals(FrameGenManager.BACKEND_LSFG_NATIVE, FrameGenManager.normalizeBackend("LSFG_NATIVE"))
        assertEquals(FrameGenManager.BACKEND_LSFG_VK, FrameGenManager.normalizeBackend(null))
        assertEquals(FrameGenManager.BACKEND_WIN_FG, FrameGenManager.normalizeBackend("bionic_fg"))
        assertEquals(FrameGenManager.BACKEND_WIN_FG, FrameGenManager.normalizeBackend("native_fg"))
        assertEquals("fifo", LsfgVkManager.presentModeForMultiplier(0))
        assertEquals("fifo", LsfgVkManager.presentModeForMultiplier(1))
        assertEquals("mailbox", LsfgVkManager.presentModeForMultiplier(2))
        assertEquals("mailbox", LsfgVkManager.presentModeForMultiplier(4))
    }
}
