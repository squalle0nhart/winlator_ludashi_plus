package com.winlator.cmod.fexcore

import com.winlator.cmod.container.Container
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FEXCorePresetManagerTest {
    @Test
    fun extremeGnLeavesSmcAndCachesAtRuntimeDefaultsAndWnCacheKnobsRemainEditable() {
        val gn = FEXCorePresetManager.getEnvVars(null, FEXCorePreset.EXTREME_GN)
        assertEquals("0", gn.get("FEX_TSOENABLED"))
        assertEquals("1", gn.get("FEX_SMALLTSCSCALE"))
        assertEquals("1", gn.get("FEX_VOLATILEMETADATA"))
        assertFalse(gn.has("FEX_SMCCHECKS"))
        assertFalse(gn.has("FEX_DISABLEL2CACHE"))
        assertFalse(gn.has("FEX_DYNAMICL1CACHE"))
        val catalog = JsonParser.parseString(File("src/main/assets/fexcore_env_vars.json").readText())
            .asJsonArray.map { it.asJsonObject["name"].asString }.toSet()
        for (id in listOf(FEXCorePreset.EXTREME, FEXCorePreset.EXTREME_TSO, FEXCorePreset.EXTREME_GN)) {
            assertTrue("$id must survive editing", catalog.containsAll(FEXCorePresetManager.getEnvVars(null, id).toSet()))
        }
    }

    @Test
    fun performanceAndExtremePresetsKeepTheirTsoModes() {
        assertEquals("1", FEXCorePresetManager.getEnvVars(null, FEXCorePreset.PERFORMANCE_TSO).get("FEX_TSOENABLED"))
        assertEquals("0", FEXCorePresetManager.getEnvVars(null, FEXCorePreset.EXTREME).get("FEX_TSOENABLED"))
        assertEquals("1", FEXCorePresetManager.getEnvVars(null, FEXCorePreset.EXTREME_TSO).get("FEX_TSOENABLED"))
        assertTrue(Container.DEFAULT_GRAPHICSDRIVERCONFIG.startsWith("vulkanVersion=1.4;"))
        assertTrue(Container.DEFAULT_GRAPHICSDRIVERCONFIG.contains(";timelineSemaphores=0;"))
    }
}
