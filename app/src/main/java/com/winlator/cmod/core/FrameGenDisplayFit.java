package com.winlator.cmod.core;

public final class FrameGenDisplayFit {
    private static final float EXACT_EPSILON_HZ = 0.5f;
    private static final float EXACT_FIT_SLACK = 0.003f;

    private FrameGenDisplayFit() {}

    /** Exact rate when available, otherwise the closest supported rate above the requested rate. */
    public static float pickRefreshRate(float[] ascendingRates, int requestedHz) {
        if (ascendingRates == null || requestedHz <= 0) return 0f;
        for (float rate : ascendingRates) {
            if (Math.abs(rate - requestedHz) < EXACT_EPSILON_HZ || rate > requestedHz) return rate;
        }
        return 0f;
    }

    public static int maxFps(float screenHz, int multiplier) {
        return screenHz > 0f && multiplier >= 2 ? Math.max(10, (int) Math.floor(screenHz / multiplier)) : 0;
    }

    public static int fittingMultiplier(float screenHz, int fps, int currentMultiplier) {
        for (int multiplier = Math.min(4, currentMultiplier - 1); multiplier >= 2; multiplier--) {
            if (fps * multiplier <= screenHz + EXACT_EPSILON_HZ) return multiplier;
        }
        return 0;
    }

    /** Pace a fraction below an exact fit so clock drift cannot leave a frame queued. */
    public static float pacedFps(int fps, int multiplier, float displayHz) {
        if (fps <= 0 || multiplier < 2 || displayHz <= 0f
                || Math.abs(fps * multiplier - displayHz) >= EXACT_EPSILON_HZ) return fps;
        return displayHz * (1f - EXACT_FIT_SLACK) / multiplier;
    }

    public static String defaultScreenSize(int width, int height) {
        if (width <= 0 || height <= 0) return "1280x720";
        float ratio = (float)Math.max(width, height) / Math.min(width, height);
        if (ratio < 1.467f) return "1280x960";
        if (ratio < 1.689f) return "1280x800";
        return "1280x720";
    }
}
