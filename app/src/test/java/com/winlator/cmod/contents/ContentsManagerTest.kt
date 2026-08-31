package com.winlator.cmod.contents

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentsManagerTest {
    @Test
    fun identifiesBundledAndCustomSources() {
        assertEquals(0, ContentsManager.getRemoteProfilesSource(ContentsManager.REMOTE_PROFILES))
        assertEquals(1, ContentsManager.getRemoteProfilesSource(ContentsManager.REMOTE_PROFILES_NICHOLASX417))
        assertEquals(2, ContentsManager.getRemoteProfilesSource(ContentsManager.REMOTE_PROFILES_THE412BANNER))
        assertEquals(3, ContentsManager.getRemoteProfilesSource("https://example.com/contents.json"))
    }
}
