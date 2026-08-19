package com.winlator.cmod.container

import com.winlator.cmod.contents.ContentProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerManagerTest {
    @Test
    fun customWinePrefixUpdatesOncePerSelectedWcp() {
        val proton = ContentProfile().apply {
            type = ContentProfile.ContentType.CONTENT_TYPE_PROTON
        }
        val version = "Proton-11.1-ge-arm64ec-steam-0"

        assertTrue(ContainerManager.needsWinePrefixUpdate(proton, version, ""))
        assertTrue(ContainerManager.needsWinePrefixUpdate(proton, version, "Proton-9.0-arm64ec"))
        assertFalse(ContainerManager.needsWinePrefixUpdate(proton, version, version))
        assertFalse(ContainerManager.needsWinePrefixUpdate(null, version, ""))
    }
}
