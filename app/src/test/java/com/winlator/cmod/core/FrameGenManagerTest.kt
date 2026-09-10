package com.winlator.cmod.core

import org.junit.Assert.*
import org.junit.Test

class FrameGenManagerTest {
    @Test
    fun nativeLsfgDisablesLegacyLayersAndPreservesOtherEnvironment() {
        for (backend in listOf(null, "lsfg_vk", "LSFG_NATIVE")) {
            assertEquals("lsfg_native", FrameGenManager.normalizeBackend(backend))
        }
        for (backend in listOf("win_fg_native", "win_fg", "bionic_fg", "native_fg")) {
            assertEquals("win_fg_native", FrameGenManager.normalizeBackend(backend))
        }
        val env = EnvVars("LSFG_MULTIPLIER=4 WIN_FG_ENABLE=1 BIONIC_FG_ENABLE=1 DXVK_HUD=fps VK_INSTANCE_LAYERS=VK_LAYER_LS_frame_generation:VK_LAYER_WIN_framegen:VK_LAYER_DISPLAYX_display_x")
        FrameGenManager.applyLaunchEnv(env)
        assertFalse(env.has("LSFG_MULTIPLIER"))
        assertFalse(env.has("WIN_FG_ENABLE"))
        assertFalse(env.has("BIONIC_FG_ENABLE"))
        assertEquals("1", env.get("DISABLE_LSFG"))
        assertEquals("1", env.get("WIN_FG_DISABLE"))
        assertEquals("1", env.get("BIONIC_FG_DISABLE"))
        assertEquals("fps", env.get("DXVK_HUD"))
        assertEquals("VK_LAYER_DISPLAYX_display_x", env.get("VK_INSTANCE_LAYERS"))
        FrameGenManager.applyLaunchEnv(env)
        assertEquals("VK_LAYER_DISPLAYX_display_x", env.get("VK_INSTANCE_LAYERS"))
    }

    @Test
    fun vegasMigratesAndSwitchesBackToDxvk() {
        assertEquals("vegas", DXWrapper.migrate("dxvk+vkd3d", "2.7.3-vegas"))
        assertEquals("dxvk+vkd3d", DXWrapper.migrate("dxvk+vkd3d", "2.3.1"))
        assertEquals("wined3d", DXWrapper.migrate("wined3d", "2.7.3-vegas"))
        assertTrue(DXWrapper.isVulkan("vegas"))
        assertTrue(DXWrapper.isVulkan("dxvk+vkd3d"))
        assertFalse(DXWrapper.isVulkan("wined3d"))
        assertEquals("2.7.3-vegas", DXWrapper.versionFor("vegas", "2.3.1", "2.3.1"))
        assertEquals("2.3.1", DXWrapper.versionFor("dxvk+vkd3d", "2.7.3-vegas", "2.3.1"))
        assertEquals("2.3.1", DXWrapper.versionFor("dxvk+vkd3d", "2.3.1", "1.10.3"))
    }
}
