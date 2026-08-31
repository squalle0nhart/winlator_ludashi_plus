package com.winlator.cmod.fexcore

import com.winlator.cmod.container.Container
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FEXCorePresetManagerTest {
    @Test
    fun performanceAndExtremePresetsKeepTheirTsoModes() {
        assertEquals("1", FEXCorePresetManager.getEnvVars(null, FEXCorePreset.PERFORMANCE_TSO).get("FEX_TSOENABLED"))
        assertEquals("0", FEXCorePresetManager.getEnvVars(null, FEXCorePreset.EXTREME).get("FEX_TSOENABLED"))
        assertEquals("1", FEXCorePresetManager.getEnvVars(null, FEXCorePreset.EXTREME_TSO).get("FEX_TSOENABLED"))
        assertTrue(Container.DEFAULT_GRAPHICSDRIVERCONFIG.startsWith("vulkanVersion=1.4;"))
        assertTrue(Container.DEFAULT_GRAPHICSDRIVERCONFIG.contains(";timelineSemaphores=0;"))
    }
}
