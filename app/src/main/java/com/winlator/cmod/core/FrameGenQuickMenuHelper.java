package com.winlator.cmod.core;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

public abstract class FrameGenQuickMenuHelper {
    public static class Settings {
        public final String backend;
        public final int multiplier;
        public final float flowScale;
        public final boolean performanceMode;
        public final int model;

        public Settings(String backend, int multiplier, float flowScale, boolean performanceMode, int model) {
            this.backend = FrameGenManager.normalizeBackend(backend);
            this.multiplier = sanitizeMultiplier(multiplier);
            this.flowScale = sanitizeFlowScale(flowScale);
            this.performanceMode = performanceMode;
            this.model = sanitizeModel(model);
        }
    }

    private FrameGenQuickMenuHelper() {}

    public static int sanitizeMultiplier(int multiplier) {
        if (multiplier < 2) return 0;
        return Math.max(2, Math.min(4, multiplier));
    }

    public static float sanitizeFlowScale(float flowScale) {
        return Math.max(0.25f, Math.min(1.0f, flowScale));
    }

    public static int sanitizeModel(int model) {
        return Math.max(0, Math.min(4, model));
    }

    public static Settings readSettings(Container container) {
        String backend = FrameGenManager.getBackend(container);
        if (FrameGenManager.BACKEND_NATIVE_FG.equals(backend)) {
            return new Settings(backend, container.getNativeFgMultiplier(), container.getLsfgFlowScale(),
                    container.getLsfgPerformanceMode(), container.getBionicFgModel());
        }
        if (FrameGenManager.BACKEND_BIONIC_FG.equals(backend)) {
            return new Settings(backend, container.getBionicFgMultiplier(), container.getBionicFgFlowScale(),
                    container.getLsfgPerformanceMode(), container.getBionicFgModel());
        }
        return new Settings(backend, container.getLsfgMultiplier(), container.getLsfgFlowScale(),
                container.getLsfgPerformanceMode(), container.getBionicFgModel());
    }

    public static Settings readSettings(Shortcut shortcut) {
        String backend = FrameGenManager.getBackend(shortcut);
        if (FrameGenManager.BACKEND_NATIVE_FG.equals(backend)) {
            return new Settings(backend, shortcut.getNativeFgMultiplier(), shortcut.getLsfgFlowScale(),
                    shortcut.getLsfgPerformanceMode(), shortcut.getBionicFgModel());
        }
        if (FrameGenManager.BACKEND_BIONIC_FG.equals(backend)) {
            return new Settings(backend, shortcut.getBionicFgMultiplier(), shortcut.getBionicFgFlowScale(),
                    shortcut.getLsfgPerformanceMode(), shortcut.getBionicFgModel());
        }
        return new Settings(backend, shortcut.getLsfgMultiplier(), shortcut.getLsfgFlowScale(),
                shortcut.getLsfgPerformanceMode(), shortcut.getBionicFgModel());
    }

    public static void applySettings(Container container, Settings settings) {
        container.setFrameGenBackend(settings.backend);
        if (FrameGenManager.BACKEND_NATIVE_FG.equals(settings.backend)) {
            container.setNativeFgMultiplier(settings.multiplier);
            container.setLsfgEnabled(false);
        } else if (FrameGenManager.BACKEND_BIONIC_FG.equals(settings.backend)) {
            container.setBionicFgMultiplier(settings.multiplier);
            container.setBionicFgFlowScale(settings.flowScale);
            container.setBionicFgModel(settings.model);
            container.setLsfgEnabled(false);
        } else {
            container.setLsfgMultiplier(settings.multiplier);
            container.setLsfgFlowScale(settings.flowScale);
            container.setLsfgPerformanceMode(settings.performanceMode);
            container.setLsfgEnabled(settings.multiplier >= 2);
        }
        container.saveData();
    }

    public static void applySettings(Shortcut shortcut, Settings settings) {
        shortcut.setFrameGenBackend(settings.backend);
        if (FrameGenManager.BACKEND_NATIVE_FG.equals(settings.backend)) {
            shortcut.setNativeFgMultiplier(settings.multiplier);
            shortcut.setLsfgEnabled(false);
        } else if (FrameGenManager.BACKEND_BIONIC_FG.equals(settings.backend)) {
            shortcut.setBionicFgMultiplier(settings.multiplier);
            shortcut.setBionicFgFlowScale(settings.flowScale);
            shortcut.setBionicFgModel(settings.model);
            shortcut.setLsfgEnabled(false);
        } else {
            shortcut.setLsfgMultiplier(settings.multiplier);
            shortcut.setLsfgFlowScale(settings.flowScale);
            shortcut.setLsfgPerformanceMode(settings.performanceMode);
            shortcut.setLsfgEnabled(settings.multiplier >= 2);
        }
        shortcut.saveData();
    }
}
