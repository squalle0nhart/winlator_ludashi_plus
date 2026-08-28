package com.winlator.cmod.container

import com.winlator.cmod.core.KeyValueSet
import org.junit.Assert.assertEquals
import org.junit.Test

class ContainerTest {
    @Test
    fun openGLDefaultsToBuiltinForNewAndFallbackContainers() {
        assertEquals("0", KeyValueSet(Container.DEFAULT_WINCOMPONENTS).get("opengl"))
        assertEquals("0", KeyValueSet(Container.FALLBACK_WINCOMPONENTS).get("opengl"))
    }
}
