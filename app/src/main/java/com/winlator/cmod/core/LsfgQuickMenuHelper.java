package com.winlator.cmod.core;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import java.util.Locale;

public abstract class LsfgQuickMenuHelper {
    public static class Settings {
        public final int multiplier;
        public final float flowScale;
        public final boolean performanceMode;

        public Settings(int multiplier, float flowScale, boolean performanceMode) {
            this.multiplier = sanitizeMultiplier(multiplier);
            this.flowScale = sanitizeFlowScale(flowScale);
            this.performanceMode = performanceMode;
        }
    }

    private LsfgQuickMenuHelper() {}

    public static int sanitizeMultiplier(int multiplier) {
        if (multiplier < 2) return 0;
        return Math.max(2, Math.min(4, multiplier));
    }

    public static float sanitizeFlowScale(float flowScale) {
        return Math.max(0.25f, Math.min(1.0f, flowScale));
    }

    public static Settings readSettings(Container container) {
        return new Settings(
                LsfgVkManager.multiplier(container),
                LsfgVkManager.flowScale(container),
                LsfgVkManager.performanceMode(container));
    }

    public static Settings readSettings(Shortcut shortcut) {
        return new Settings(
                LsfgVkManager.multiplier(shortcut),
                LsfgVkManager.flowScale(shortcut),
                LsfgVkManager.performanceMode(shortcut));
    }

    public static boolean isAvailable(Container container) {
        return LsfgVkManager.isArmed(container);
    }

    public static boolean isAvailable(Shortcut shortcut) {
        return LsfgVkManager.isArmed(shortcut);
    }

    public static void applySettings(Container container, Settings settings) {
        int multiplier = sanitizeMultiplier(settings.multiplier);
        float flowScale = sanitizeFlowScale(settings.flowScale);
        container.putExtra("lsfgMultiplier", String.valueOf(multiplier));
        container.putExtra("lsfgFlowScale", String.format(Locale.US, "%.2f", flowScale));
        container.putExtra("lsfgPerformanceMode", String.valueOf(settings.performanceMode));
        container.putExtra("lsfgEnabled", multiplier >= 2 ? "true" : "false");
        container.saveData();
        LsfgVkManager.updateConfigAtRuntime(container, multiplier >= 2, multiplier >= 2 ? multiplier : 1, flowScale, settings.performanceMode);
    }

    public static void applySettings(Shortcut shortcut, Settings settings) {
        int multiplier = sanitizeMultiplier(settings.multiplier);
        float flowScale = sanitizeFlowScale(settings.flowScale);
        shortcut.setLsfgMultiplier(multiplier);
        shortcut.setLsfgFlowScale(flowScale);
        shortcut.setLsfgPerformanceMode(settings.performanceMode);
        shortcut.setLsfgEnabled(multiplier >= 2);
        shortcut.saveData();
        LsfgVkManager.updateConfigAtRuntime(shortcut, multiplier >= 2, multiplier >= 2 ? multiplier : 1, flowScale, settings.performanceMode);
    }
}
