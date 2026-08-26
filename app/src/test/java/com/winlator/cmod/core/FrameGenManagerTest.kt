package com.winlator.cmod.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameGenManagerTest {
    @Test
    fun legacyBackendAndLsfgPresentModeMigrateCleanly() {
        assertEquals(FrameGenManager.BACKEND_WIN_FG, FrameGenManager.normalizeBackend("bionic_fg"))
        assertEquals("fifo", LsfgVkManager.presentModeForMultiplier(0))
        assertEquals("fifo", LsfgVkManager.presentModeForMultiplier(1))
        assertEquals("mailbox", LsfgVkManager.presentModeForMultiplier(2))
        assertEquals("mailbox", LsfgVkManager.presentModeForMultiplier(4))
    }
}
