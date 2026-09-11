package com.winlator.cmod.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Release310FeatureTest {
    @Test public void frameGenerationFitsOnlyAtOrAboveRequestedRate() {
        float[] rates = {60f, 90f, 120f, 144f};
        assertEquals(60f, FrameGenDisplayFit.pickRefreshRate(rates, 60), 0.01f);
        assertEquals(120f, FrameGenDisplayFit.pickRefreshRate(rates, 100), 0.01f);
        assertEquals(0f, FrameGenDisplayFit.pickRefreshRate(rates, 240), 0.01f);
        assertEquals(72, FrameGenDisplayFit.maxFps(144f, 2));
        assertEquals(2, FrameGenDisplayFit.fittingMultiplier(120f, 50, 4));
        assertEquals(29.91f, FrameGenDisplayFit.pacedFps(30, 2, 60f), 0.01f);
        assertEquals(50f, FrameGenDisplayFit.pacedFps(50, 2, 120f), 0.01f);
    }

    @Test public void newContainerDefaultFollowsPanelShape() {
        assertEquals("1280x720", FrameGenDisplayFit.defaultScreenSize(2400, 1080));
        assertEquals("1280x800", FrameGenDisplayFit.defaultScreenSize(2560, 1600));
        assertEquals("1280x800", FrameGenDisplayFit.defaultScreenSize(1500, 1000));
        assertEquals("1280x960", FrameGenDisplayFit.defaultScreenSize(1440, 1080));
    }
}
