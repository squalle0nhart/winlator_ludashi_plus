package com.winlator.star.ui.theme;

/**
 * Standalone shim of Bannerlator's {@code AppThemeState}. The HUD views ask this for a single value —
 * the current accent colour (ARGB int) — which they use to tint accents in the Fusion / Performance
 * overlays. Integrators set their brand colour once via {@link #setCurrentAccentArgb}.
 *
 * <p>Default is Bannerlator's violet accent ({@code 0xFFA374FF}). Reads are lock-free (volatile).
 */
public final class AppThemeState {
    private AppThemeState() {}

    private static volatile int currentAccentArgb = 0xFFA374FF;

    /** Current accent colour as a packed ARGB int, consumed by every HUD overlay. */
    public static int getCurrentAccentArgb() {
        return currentAccentArgb;
    }

    /** Set the accent colour integrators want the HUD to use (packed ARGB, e.g. {@code 0xFF00E5FF}). */
    public static void setCurrentAccentArgb(int argb) {
        currentAccentArgb = argb;
    }
}
