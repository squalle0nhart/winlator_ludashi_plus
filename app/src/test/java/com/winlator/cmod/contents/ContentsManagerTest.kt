package com.winlator.cmod.contents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentsManagerTest {
    @Test
    fun acceptsExperimentalFexUnixLibraries() {
        assertTrue(ContentsManager.FEXCORE_TRUST_FILES.contains("\${libdir}/wine/aarch64-unix/libwow64fex.so"))
        assertTrue(ContentsManager.FEXCORE_TRUST_FILES.contains("\${libdir}/wine/aarch64-unix/libarm64ecfex.so"))
    }

    @Test
    fun identifiesBundledAndCustomSources() {
        assertEquals(0, ContentsManager.getRemoteProfilesSource(ContentsManager.REMOTE_PROFILES))
        assertEquals(1, ContentsManager.getRemoteProfilesSource(ContentsManager.REMOTE_PROFILES_NICHOLASX417))
        assertEquals(2, ContentsManager.getRemoteProfilesSource(ContentsManager.REMOTE_PROFILES_THE412BANNER))
        assertEquals(3, ContentsManager.getRemoteProfilesSource("https://example.com/contents.json"))
    }
}
