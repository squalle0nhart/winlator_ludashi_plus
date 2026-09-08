package com.winlator.cmod.box64

import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Box64PresetManagerTest {
    @Test
    fun extremeTiersKeepDistinctTuningAndAll32VariablesAreEditable() {
        val original = Box64PresetManager.getEnvVars("box64", null, Box64Preset.EXTREME)
        val community = Box64PresetManager.getEnvVars("box64", null, Box64Preset.EXTREME_2)
        for ((key, values) in mapOf(
            "FASTROUND" to ("1" to "2"),
            "FORWARD" to ("512" to "1024"),
            "WEAKBARRIER" to ("1" to "2"),
            "DIRTY" to ("1" to "0"),
            "NATIVEFLAGS" to ("0" to "1"),
        )) {
            assertEquals(values.first, original.get("BOX64_DYNAREC_$key"))
            assertEquals(values.second, community.get("BOX64_DYNAREC_$key"))
        }
        val catalog = JsonParser.parseString(File("src/main/assets/box64_env_vars.json").readText())
            .asJsonArray.map { it.asJsonObject }.associateBy { it["name"].asString }
        assertEquals(32, community.toStringArray().size)
        assertEquals(community.toSet(), catalog.keys)
        for (key in community) {
            assertTrue("$key value must survive editing", catalog.getValue(key)["values"]
                .asJsonArray.any { it.asString == community.get(key) })
        }
        assertFalse(Box64PresetManager.getEnvVars("box86", null, Box64Preset.EXTREME_2)
            .any { it.startsWith("BOX64_") })
    }
}
